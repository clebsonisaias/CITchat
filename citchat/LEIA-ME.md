# CITchat

CITchat é um **cliente não oficial do Telegram** para Android, baseado no código aberto do
[Telegram X](https://github.com/TGX-Android/Telegram-X) (GPLv3). Os usuários entram com o número
de telefone e conversam com qualquer pessoa do Telegram.

- Pacote: `br.com.citmax.citchat`
- Licença: GNU GPL v3 (o código-fonte do CITchat precisa continuar público)

Tudo o que é específico do CITchat fica em `citchat/` e em `.github/workflows/citchat-apk.yml`.
As mudanças no código do Telegram X foram mantidas pequenas, para facilitar receber atualizações.

## O que foi alterado em relação ao Telegram X

| Área | Mudança |
|---|---|
| Nome | `app.name=CITchat` no build. `core/Branding.java` troca "Telegram X" pelo nome do app em todos os textos, inclusive nas traduções baixadas de translations.telegram.org |
| Ícones | Ícone novo (balão com "C") no launcher, no ícone adaptativo e monocromático, na notificação, na animação da introdução e nas telas de senha e de chamada. O logo do Telegram foi removido |
| Introdução | A primeira página avisa que o CITchat é um cliente não oficial que usa a API do Telegram (exigência dos termos da API) |
| Atualizações | O atualizador só aceita APKs com o nome `CITchat-*`, então não oferece mais builds do Telegram X |
| Manifest | As ações das notificações (encerrar chamada, responder, player, localização) usam `${applicationId}` e funcionam com o pacote novo |
| Contas do Android | A conta de sincronização aparece como "CITchat" |

## Compilar (GitHub Actions)

O Telegram X só compila em Linux ou macOS e leva de 1 a 2 horas. Por isso a compilação roda na nuvem.

1. Gere a chave de assinatura (uma única vez):
   `powershell -ExecutionPolicy Bypass -File citchat\gerar-keystore.ps1`
   Os arquivos ficam em `%USERPROFILE%\CITchat-segredos`. **Faça backup dessa pasta.** Sem ela
   não é possível publicar atualizações do app com a mesma assinatura.
2. Configure o GitHub (login, fork e Secrets):
   `powershell -ExecutionPolicy Bypass -File citchat\configurar-github.ps1`
3. Envie o código e rode o workflow **CITchat APK** (aba Actions > Run workflow), escolhendo:
   - `arm64`: celulares modernos
   - `arm32`: aparelhos de 32 bits
   - `universal`: os dois
4. Baixe o APK em *Artifacts*, na página da execução, ou com
   `gh run download -R <usuario>/CITchat`.
5. Para publicar uma versão, crie uma tag `v*` (por exemplo `git tag v1.0.0 && git push origin v1.0.0`).
   O workflow compila o APK universal e cria um Release com ele.

### Secrets do repositório

| Secret | Origem |
|---|---|
| `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` | https://my.telegram.org > API development tools |
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` | `CITchat-segredos\KEYSTORE_BASE64.txt` e `KEYSTORE_PASSWORD.txt` |
| `GOOGLE_SERVICES_JSON` (opcional) | `google-services.json` de um projeto Firebase com o app Android `br.com.citmax.citchat` |

Sem `GOOGLE_SERVICES_JSON` o workflow usa um arquivo provisório: o app funciona, mas **sem
notificações push** com o app fechado.

## Instalar no celular pelo cabo USB

1. No Android, ative *Opções do desenvolvedor* e *Depuração USB*, e aceite o aviso ao conectar.
2. `adb install -r CITchat-*.apk`

## Pendências (próximas etapas)

- **Notificações push:** criar o projeto Firebase, cadastrar o secret `GOOGLE_SERVICES_JSON`
  (as impressões digitais SHA-1 e SHA-256 da chave estão em `CITchat-segredos\LEIA-ME.txt`) e
  configurar a chave do Firebase no app em https://my.telegram.org.
- **Suporte e links:** algumas telas de erro raras ainda citam os canais do Telegram X (@tgx_log,
  @tgandroidtests). Trocar pelos canais do CITchat quando existirem.
- **Política de privacidade:** o link aponta para telegram.org/privacy. Publicar a política do CITchat.
- **Mapas:** a chave do Google Maps no `AndroidManifest.xml` é do Telegram X e não funciona com o
  pacote novo. Criar uma chave própria.
- **Cores:** o tema continua azul. A cor da marca (`#0E7C86`) está em `res/values/citchat_colors.xml`.
- **Ícone oficial:** o ícone atual é provisório. Para trocar a arte, edite `citchat/branding/citchat-icon.svg`,
  os vetores em `app/src/main/res/drawable/` e rode `citchat\branding\gerar-icones.ps1`.

## Termos da API do Telegram (resumo)

- O nome do app não pode conter "Telegram", a menos que venha depois de "Unofficial".
- Não use o logo do Telegram.
- Deixe claro, na loja e no app, que é um cliente não oficial que usa a API do Telegram.
- Use o seu próprio `api_id`.
- Mantenha o suporte a mensagens patrocinadas.
- Informe na loja se houver anúncios ou outra forma de monetização.
- Não use dados do Telegram para treinar IA.

Texto completo: https://core.telegram.org/api/terms

## Receber atualizações do Telegram X

```bash
git remote add upstream https://github.com/TGX-Android/Telegram-X
git fetch upstream main
git merge upstream/main
```

Ou use o botão *Sync fork* no GitHub. Conflitos, se aparecerem, costumam ficar nos arquivos
listados em "O que foi alterado".
