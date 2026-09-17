# CITchat

CITchat é um **cliente não oficial do Telegram** para Android, baseado no código aberto do
[Telegram X](https://github.com/TGX-Android/Telegram-X) (GPLv3). Os usuários entram com o número
de telefone e conversam com qualquer pessoa do Telegram.

- Pacote: `br.com.citmax.citchat`
- Licença: GNU GPL v3 (o código-fonte do CITchat precisa continuar público)
- Marca: o nome e o símbolo da CITmax pertencem à CITmax e **não** estão cobertos pela GPL. Quem
  reaproveitar este código em outro app deve trocar a marca.

Tudo o que é específico do CITchat fica em `citchat/` e em `.github/workflows/citchat-apk.yml`.
As mudanças no código do Telegram X foram mantidas pequenas, para facilitar receber atualizações.

## O que foi alterado em relação ao Telegram X

| Área | Mudança |
|---|---|
| Nome | `app.name=CITchat` no build. `core/Branding.java` troca "Telegram X" pelo nome do app em todos os textos, inclusive nas traduções baixadas de translations.telegram.org |
| Ícones | Logo do CITchat (`citchat/branding/logo-citchat.svg`: hexágono CITmax com balões de conversa) no launcher (fundo branco) e no ícone adaptativo; a silhueta do logo é usada na notificação, no ícone monocromático e nas telas de senha e de chamada. Na introdução, o logo aparece no lugar da esfera do Telegram. O logo do Telegram foi removido |
| Cores | Paleta do Manual da marca nos temas claro (padrão) e escuro: cabeçalho em Conexão profunda `#036271`, destaques e botão flutuante em Inovação `#00C896`, ícones e confirmações em Tecnologia em movimento `#008B87`. Aplicada por `citchat/branding/aplicar-paleta.ps1`. O fundo das conversas é liso, sem o padrão do Telegram |
| Introdução | A primeira página avisa que o CITchat é um cliente não oficial que usa a API do Telegram (exigência dos termos da API) |
| Atualizações | O atualizador só aceita APKs com o nome `CITchat-*`, então não oferece mais builds do Telegram X |
| Manifest | As ações das notificações (encerrar chamada, responder, player, localização) usam `${applicationId}` e funcionam com o pacote novo |
| Contas do Android | A conta de sincronização aparece como "CITchat" |
| Transcrição de áudio | Opção "Transcrever áudio" nas mensagens de voz e de vídeo (Android 10 ou mais novo). Roda no aparelho com o [Vosk](https://alphacephei.com/vosk); o pacote de voz em português (31 MB) é baixado no primeiro uso, e o áudio não sai do celular |
| Pix e boleto | Mensagens com código Pix copia e cola ou boleto válido (CRC16 do Pix, dígitos verificadores do boleto) ganham o botão "Copiar Pix"/"Copiar boleto" e a opção no menu da mensagem. "Cobrar com Pix" e, em grupos, "Racha conta" (menu ⋮ da conversa) geram um Pix estático com a chave do próprio usuário e enviam o QR e o código. Nenhum dinheiro passa pelo app; chave, nome e cidade ficam no celular (`PixCode`, `PixCharge`) |
| Alerta de golpe | Tudo no celular (`ScamGuard`): aviso acima das mensagens quando alguém fora dos contatos pede dinheiro, diz que mudou de número ou pede código de verificação; aviso antes de abrir link que imita banco ou site do governo, esconde o endereço, usa letras parecidas, número IP ou encurtador |
| Figurinha de foto | "Criar figurinha" nas fotos e "Figurinha de uma foto" no menu ⋮ da conversa: recorte automático com o ML Kit (Google Play services, só na variante `latest`), contorno branco e prévia antes de enviar |
| Economia de internet | No topo de Dados e armazenamento: modos prontos (normal, vídeo só no Wi-Fi, economia máxima), dados móveis usados no ciclo e franquia do plano com aviso em 80%, 90% e 100%. O uso do celular inteiro depende do "acesso ao uso" do Android, que o usuário concede (`DataSaving`) |
| Anúncio da CITmóvel | Linha "Patrocinado" no topo da lista de conversas, que abre citmax.com.br/citmovel (pacote `org.thunderdog.challegram.citchat`, classe `HouseAd`). Aparece em no máximo uma sessão por dia, e o X esconde até o dia seguinte. Não usa rede de anúncios, não faz requisição e não lê as conversas. Fica de fora das pastas, do arquivo e do modo "ocultar arquivo" |

## Compilar (GitHub Actions)

O Telegram X só compila em Linux ou macOS e leva de 1 a 2 horas. Por isso a compilação roda na nuvem.

1. Gere a chave de assinatura (uma única vez):
   `powershell -ExecutionPolicy Bypass -File citchat\gerar-keystore.ps1`
   Os arquivos ficam em `%USERPROFILE%\CITchat-segredos`. **Faça backup dessa pasta.** Sem ela
   não é possível publicar atualizações do app com a mesma assinatura.
2. Configure o GitHub (login, fork e Secrets):
   `powershell -ExecutionPolicy Bypass -File citchat\configurar-github.ps1`
   Se o GitHub CLI já estiver logado, dá para gravar só as credenciais do Telegram por uma janela:
   `powershell -STA -ExecutionPolicy Bypass -File citchat\credenciais-telegram.ps1`
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
2. Baixe a última compilação e instale:
   `powershell -ExecutionPolicy Bypass -File citchat\instalar-no-celular.ps1`
   (ou, com um APK já baixado, `adb install -r CITchat-*.apk`)

## Pendências (próximas etapas)

- **Notificações push:** criar o projeto Firebase com o app Android `br.com.citmax.citchat`
  (as impressões digitais SHA-1 e SHA-256 da chave estão em `CITchat-segredos\LEIA-ME.txt`),
  cadastrar o secret `GOOGLE_SERVICES_JSON` e enviar a *service account* do Firebase em
  https://my.telegram.org > API development tools > **FCM credentials**.
- **Suporte e links:** algumas telas de erro raras ainda citam os canais do Telegram X (@tgx_log,
  @tgandroidtests). Trocar pelos canais do CITchat quando existirem.
- **Política de privacidade:** o link aponta para telegram.org/privacy. Publicar a política do CITchat.
- **Mapas:** a chave do Google Maps no `AndroidManifest.xml` é do Telegram X e não funciona com o
  pacote novo. Criar uma chave própria.
- **Fontes:** o Manual da marca usa Righteous (fonte da marca) e Montserrat (apoio); o app ainda usa Roboto.
- **Anúncio da CITmóvel:** o texto e o preço (`CITchatAdText` em `app/src/main/res/values*/citchat_strings.xml`)
  foram tirados do site em 16/09/2026. Atualize quando o plano mudar. Ao publicar, marque "contém anúncios"
  no Play Console e cite o anúncio na descrição da loja, como pedem os termos da API (seção 3.2).
- **Acesso ao uso:** a permissão `PACKAGE_USAGE_STATS` é opcional e concedida pelo próprio usuário nas
  configurações do Android. No formulário de segurança de dados da Play Store, explique que o app só lê o
  total de dados móveis, no aparelho, para comparar com a franquia.
- **Figurinhas:** o recorte usa o ML Kit do Google, que precisa do Google Play services e baixa o modelo no
  primeiro uso. Em celulares sem os serviços do Google a opção não aparece.
- **Logo:** para trocar, substitua `citchat/branding/logo-citchat.svg` e rode
  `powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1 -FromSvg citchat\branding\logo-citchat.svg`
  (o SVG é renderizado pelo Microsoft Edge e todos os ícones são gerados de novo).

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
