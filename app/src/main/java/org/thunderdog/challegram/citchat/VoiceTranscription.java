/*
 * This file is a part of CITchat, a modified version of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.citchat;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.LruCache;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Transcribes voice and video messages on the device with Vosk (https://alphacephei.com/vosk).
 * Audio never leaves the phone; the Portuguese model is downloaded once, on first use.
 */
public final class VoiceTranscription {
  private static final String MODEL_NAME = "vosk-model-small-pt-0.3";
  private static final String MODEL_URL = "https://alphacephei.com/vosk/models/" + MODEL_NAME + ".zip";
  private static final long MODEL_DOWNLOAD_SIZE = 31L * 1024 * 1024;
  private static final String MODEL_READY_MARKER = ".ready";
  private static final int TARGET_SAMPLE_RATE = 16000;

  private static final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "CITchatTranscription");
    thread.setPriority(Thread.MIN_PRIORITY);
    return thread;
  });
  private static final LruCache<Integer, String> results = new LruCache<>(64);

  // Accessed only on the executor thread
  private static Model model;
  // Accessed only on the UI thread
  private static boolean isBusy;

  private VoiceTranscription () { }

  public static boolean canTranscribe (@Nullable TdApi.Message message) {
    // Ogg/Opus voice notes can be decoded by MediaExtractor starting with Android 10
    return message != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getFile(message) != null;
  }

  private static @Nullable TdApi.File getFile (TdApi.Message message) {
    switch (message.content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return ((TdApi.MessageVoiceNote) message.content).voiceNote.voice;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return ((TdApi.MessageVideoNote) message.content).videoNote.video;
    }
    return null;
  }

  public static void transcribe (ViewController<?> context, TdApi.Message message) {
    TdApi.File file = getFile(message);
    if (file == null) {
      return;
    }
    String cached = results.get(file.id);
    if (cached != null) {
      showResult(context, cached);
      return;
    }
    if (isBusy) {
      UI.showToast(R.string.CITchatTranscribeBusy, Toast.LENGTH_SHORT);
      return;
    }
    if (isModelInstalled()) {
      start(context, file);
      return;
    }
    context.showOptions(
      Lang.getString(R.string.CITchatTranscribeModelPrompt, BuildConfig.PROJECT_NAME, Strings.buildSize(MODEL_DOWNLOAD_SIZE)),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.CITchatTranscribeModelDownload), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_file_download_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done && !isBusy) {
          start(context, file);
        }
        return true;
      }
    );
  }

  private static void start (ViewController<?> context, TdApi.File file) {
    isBusy = true;
    boolean needModel = !isModelInstalled();
    UI.showToast(needModel ? R.string.CITchatTranscribeModelDownloading : R.string.CITchatTranscribing, Toast.LENGTH_SHORT);
    context.tdlib().client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), result -> {
      if (result.getConstructor() != TdApi.File.CONSTRUCTOR) {
        Log.e("Unable to download file for transcription: %s", result);
        UI.post(() -> finish(context, file.id, null, R.string.CITchatTranscribeFailed));
        return;
      }
      String path = ((TdApi.File) result).local.path;
      executor.execute(() -> {
        String text = null;
        @StringRes int error = 0;
        try {
          if (!isModelInstalled()) {
            downloadModel();
            UI.post(() -> UI.showToast(R.string.CITchatTranscribing, Toast.LENGTH_SHORT));
          }
          text = recognize(path);
        } catch (Throwable t) {
          Log.e("Transcription failed", t);
          error = R.string.CITchatTranscribeFailed;
        }
        final String finalText = text;
        final int finalError = error;
        UI.post(() -> finish(context, file.id, finalText, finalError));
      });
    });
  }

  private static void finish (ViewController<?> context, int fileId, @Nullable String text, @StringRes int error) {
    isBusy = false;
    if (text == null) {
      UI.showToast(error != 0 ? error : R.string.CITchatTranscribeFailed, Toast.LENGTH_SHORT);
      return;
    }
    results.put(fileId, text);
    if (context.isDestroyed()) {
      UI.showToast(R.string.CITchatTranscribeDone, Toast.LENGTH_SHORT);
    } else {
      showResult(context, text);
    }
  }

  private static void showResult (ViewController<?> context, String text) {
    boolean isEmpty = text.isEmpty();
    CharSequence info = isEmpty ? Lang.getString(R.string.CITchatTranscribeEmpty) : text;
    if (isEmpty) {
      context.showOptions(info, new int[] {R.id.btn_cancel}, new String[] {Lang.getString(R.string.CITchatClose)}, new int[] {ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_cancel_24}, null);
      return;
    }
    context.showOptions(
      info,
      new int[] {R.id.btn_messageCopy, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Copy), Lang.getString(R.string.CITchatClose)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_content_copy_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_messageCopy) {
          UI.copyText(text, R.string.CopiedText);
        }
        return true;
      }
    );
  }

  // Model

  private static File modelDir () {
    return new File(UI.getAppContext().getFilesDir(), MODEL_NAME);
  }

  private static boolean isModelInstalled () {
    return new File(modelDir(), MODEL_READY_MARKER).exists();
  }

  private static void downloadModel () throws IOException {
    File target = modelDir();
    File tempDir = new File(UI.getAppContext().getFilesDir(), MODEL_NAME + ".tmp");
    File zip = new File(UI.getAppContext().getCacheDir(), MODEL_NAME + ".zip");
    deleteRecursively(tempDir);
    deleteRecursively(target);

    HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(60000);
    try {
      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("Model download failed: HTTP " + connection.getResponseCode());
      }
      try (InputStream in = new BufferedInputStream(connection.getInputStream()); OutputStream out = new FileOutputStream(zip)) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
      }
    } finally {
      connection.disconnect();
    }

    try {
      unzipModel(zip, tempDir);
      if (!new File(tempDir, MODEL_READY_MARKER).createNewFile() || !tempDir.renameTo(target)) {
        throw new IOException("Unable to install the model");
      }
    } finally {
      //noinspection ResultOfMethodCallIgnored
      zip.delete();
      deleteRecursively(tempDir);
    }
  }

  // The archive has a single top-level folder with the model name; its content goes straight into targetDir
  private static void unzipModel (File zip, File targetDir) throws IOException {
    String targetPath = targetDir.getCanonicalPath() + File.separator;
    try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zip)))) {
      ZipEntry entry;
      byte[] buffer = new byte[64 * 1024];
      while ((entry = in.getNextEntry()) != null) {
        String name = entry.getName();
        int slash = name.indexOf('/');
        String relative = slash >= 0 ? name.substring(slash + 1) : name;
        if (relative.isEmpty()) {
          continue;
        }
        File out = new File(targetDir, relative);
        if (!out.getCanonicalPath().startsWith(targetPath)) {
          throw new IOException("Invalid entry in model archive: " + name);
        }
        if (entry.isDirectory()) {
          if (!out.isDirectory() && !out.mkdirs()) {
            throw new IOException("Unable to create " + out);
          }
          continue;
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Unable to create " + parent);
        }
        try (OutputStream os = new FileOutputStream(out)) {
          int read;
          while ((read = in.read(buffer)) != -1) {
            os.write(buffer, 0, read);
          }
        }
      }
    }
  }

  private static void deleteRecursively (File file) {
    if (!file.exists()) {
      return;
    }
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  // Recognition

  private static String recognize (String path) throws IOException {
    if (model == null) {
      LibVosk.setLogLevel(LogLevel.WARNINGS);
      model = new Model(modelDir().getAbsolutePath());
    }
    StringBuilder text = new StringBuilder();
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    Recognizer recognizer = null;
    try {
      extractor.setDataSource(path);
      MediaFormat format = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat trackFormat = extractor.getTrackFormat(i);
        String mime = trackFormat.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          extractor.selectTrack(i);
          format = trackFormat;
          break;
        }
      }
      if (format == null) {
        throw new IOException("No audio track");
      }
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();

      int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      boolean inputDone = false;
      while (true) {
        if (!inputDone) {
          int inputIndex = codec.dequeueInputBuffer(10000);
          if (inputIndex >= 0) {
            ByteBuffer input = codec.getInputBuffer(inputIndex);
            int size = input != null ? extractor.readSampleData(input, 0) : -1;
            if (size < 0) {
              codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              codec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), 0);
              extractor.advance();
            }
          }
        }
        int outputIndex = codec.dequeueOutputBuffer(info, 10000);
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat outputFormat = codec.getOutputFormat();
          sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } else if (outputIndex >= 0) {
          ByteBuffer output = codec.getOutputBuffer(outputIndex);
          if (output != null && info.size > 0) {
            byte[] pcm = new byte[info.size];
            output.position(info.offset);
            output.get(pcm, 0, info.size);
            int factor = sampleRate % TARGET_SAMPLE_RATE == 0 ? sampleRate / TARGET_SAMPLE_RATE : 1;
            if (recognizer == null) {
              recognizer = new Recognizer(model, (float) (sampleRate / factor));
            }
            byte[] mono = toMono(pcm, Math.max(1, channels), factor);
            if (mono.length > 0 && recognizer.acceptWaveForm(mono, mono.length)) {
              appendResult(text, recognizer.getResult());
            }
          }
          codec.releaseOutputBuffer(outputIndex, false);
          if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            break;
          }
        }
      }
      if (recognizer != null) {
        appendResult(text, recognizer.getFinalResult());
      }
    } finally {
      if (recognizer != null) {
        recognizer.close();
      }
      if (codec != null) {
        try {
          codec.stop();
        } catch (Throwable ignored) { }
        codec.release();
      }
      extractor.release();
    }
    if (text.length() > 0) {
      text.setCharAt(0, Character.toUpperCase(text.charAt(0)));
    }
    return text.toString();
  }

  // 16-bit little-endian PCM: mixes the channels and averages every `factor` frames (48 kHz -> 16 kHz)
  private static byte[] toMono (byte[] pcm, int channels, int factor) {
    int frames = pcm.length / (2 * channels);
    int outFrames = frames / factor;
    byte[] out = new byte[outFrames * 2];
    for (int i = 0; i < outFrames; i++) {
      int sum = 0;
      for (int k = 0; k < factor; k++) {
        int frame = i * factor + k;
        for (int channel = 0; channel < channels; channel++) {
          int index = (frame * channels + channel) * 2;
          sum += (short) ((pcm[index] & 0xff) | (pcm[index + 1] << 8));
        }
      }
      int sample = sum / (factor * channels);
      out[i * 2] = (byte) sample;
      out[i * 2 + 1] = (byte) (sample >> 8);
    }
    return out;
  }

  private static void appendResult (StringBuilder text, String json) {
    try {
      String part = new JSONObject(json).optString("text").trim();
      if (!part.isEmpty()) {
        if (text.length() > 0) {
          text.append(' ');
        }
        text.append(part);
      }
    } catch (Throwable t) {
      Log.w("Unable to parse Vosk result: %s", json);
    }
  }
}
