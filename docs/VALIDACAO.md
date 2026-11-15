# Validação — SFMD 0.1.0

Atualização 0.1.2 (15/11/2026): 12 testes unitários passaram, incluindo sete testes de manifesto, cache, alternativa da API, redirects, tamanho e rejeição de dados inválidos. Android Lint passou sem erros. `UpdateHeaderTest` confirmou a consulta real de `update.json` público (release 0.1.1 na ocasião), o estado atualizado na instalação 0.1.2 e a presença do texto verde e navegação para Configurações usando uma oferta de teste injetada. A captura visual foi conferida localmente e não é parte da release. O motor de mídia não foi alterado nesta versão.

Data: 15/11/2026. Ambiente: Windows, Android SDK 37, emuladores x86_64. Nenhum aparelho físico ARM64 estava conectado. Não confundir APK ARM64 compilado com APK ARM64 testado em aparelho.

## Motor

Runtime executado: yt-dlp 2026.10.26, CPython 3.12.11, QuickJS 2025-04-26, EJS 0.8.0, FFmpeg e FFprobe 9.0.2. Todos incluídos no APK; a preparação inicial usa recursos locais.

`EngineProofTest` consultou o vídeo público `https://www.youtube.com/watch?v=jNQXAC9IVRw`, baixou vídeo com áudio, converteu MP3 com metadados/capa e publicou os dois arquivos. MediaMetadataRetriever confirmou áudio/vídeo no MP4 e áudio no MP3.

O FFmpeg de `io.github.junkfood02...:ffmpeg:0.18.1` falhou no emulador de 16 KB por alinhamento de `libwebp.so` e `libsharpyuv.so`. Foi substituído por `io.github.deniscerri:ytdlnis_packages.ffmpeg:9.0.2`, mantendo os binários sem novo strip. A mesma prova completa passou no emulador cujo `getconf PAGE_SIZE` retornou `16384`: vídeo de 533.931 bytes e MP3 de 663.479 bytes. O emulador normal usa páginas de 4096 bytes.

Isso valida x86_64 nos dois tamanhos de página. ARM64 e o comportamento dos binários em Android 8/9 ainda exigem testes físicos ou imagens apropriadas.

A prova foi repetida em instalação limpa do APK release assinado no emulador 16 KB e passou em 58,6 s. Os resultados foram publicados diretamente em `content://media/external/downloads/`: MP4 com áudio/vídeo e MP3 válido. O APK de instrumentação foi assinado com a mesma chave apenas para executar as verificações; não fornece componentes de download ao aplicativo.

## URLs fornecidas pelo usuário

| Fonte e tipo | Consulta | Download com áudio | Evidência |
|---|---|---|---|
| YouTube `zuSDmpKMX1w` | Passou; 45 formatos, duração de 309 s | Passou | MP4 de 6.079.623 bytes, vídeo e áudio confirmados |
| Instagram reel `DfvYB1EKfQ3` | Passou; 11 formatos | Passou, qualidade disponível de 1280 px de altura | MP4 de 1.027.673 bytes, vídeo e áudio confirmados |
| TikTok `7678064084432030996` | Passou; 10 formatos | Passou, qualidade disponível de 576 px de altura | MP4 de 191.733 bytes, vídeo e áudio confirmados |
| Facebook | Não testado | Não testado | Nenhum link de Facebook fornecido |

No primeiro teste, pedir artificialmente 360p aos links de Instagram e TikTok falhou porque eles não ofereciam essa resolução. A repetição selecionou uma altura realmente presente. A interface só apresenta alturas retornadas pela consulta. O mapeamento de erro foi corrigido para não confundir formato indisponível com conteúdo removido.

Esses resultados se aplicam às URLs e à rede testadas, não a todo o catálogo das plataformas. Conteúdo privado, login, região, idade, DRM e mudanças nos sites podem mudar o resultado.

## Interface e execução

- Build debug/release por ABI; testes unitários de URL, nome de arquivo e regras do atualizador.
- Android Lint sem erros após correções de compatibilidade API 26/29 e propriedades do SDK. Avisos de versões mais novas de bibliotecas não foram mascarados.
- Interface real com a logo fornecida; recebimento ACTION_SEND; consulta e download pelo botão.
- Download iniciado na interface concluiu com o app em segundo plano, usando serviço foreground `dataSync` e notificação; histórico gravado em Room.
- Compose: rascunhos independentes, erro para URL inválida, cancelamento de consulta e liberação do contador de operação.
- Alterações solicitadas: Download do dispositivo como padrão; pastas de música e vídeo independentes; permissões iniciais; diálogo Música/Vídeo para links compartilhados; nome editável antes de baixar. Testes correspondentes em InterfaceTest e StoragePreferencesTest.
- Cinco testes instrumentados dessas alterações passaram: rascunhos/URL inválida, cancelamento, escolha Música/Vídeo no compartilhamento, independência das preferências e publicação dos dois tipos diretamente em `Download/`.
- A permissão de notificações foi observada na primeira abertura no emulador 16 KB; o sistema também listou `com.nsk.sfmd/.MainActivity` como destino de ACTION_SEND `text/plain`.
- Fluxo manual real no emulador 16 KB: link compartilhado → escolher Música → consulta automática → editar nome para `Minha musica SFMD` → escolher `Music` por SAF sem alterar o destino de vídeos → baixar com tela apagada. Resultado: `Music/Minha musica SFMD.mp3`, 7.572.315 bytes, tarefa concluída, temporários removidos.

## Fixtures de verificação do atualizador

O teste opcional `UpdateValidationTest` espera, em `files/fixtures/` do aplicativo instalado 0.1.0 debug:

- `current-debug.apk`: 0.1.0 / versionCode 1, mesma assinatura.
- `next-debug.apk`: 0.1.1 / versionCode 2, mesma assinatura.
- `next-release.apk`: 0.1.1 / versionCode 2, assinatura release diferente.

É possível copiá-los primeiro para `/data/local/tmp/` e usar `adb shell run-as com.nsk.sfmd cp ... files/fixtures/...`. O teste verifica aceitação da assinatura correta e rejeição de hash, tamanho, versão, ABI e assinatura incorretos, além do bloqueio durante consulta ativa. Sem fixtures, ele é explicitamente ignorado.

O teste com os três APKs reais passou: candidato 0.1.1/code 2 assinado como debug aceito; digest, tamanho, versão, ABI e assinatura release diferentes rejeitados; instalação bloqueada com consulta ativa.

Uma atualização local real de debug 0.1.0/code 1 para 0.1.1/code 2 via instalador ADB também foi executada. O SHA-256 do DataStore permaneceu idêntico e a base Room foi mantida. Isso não substitui a validação pelo GitHub Releases e pela confirmação interativa do instalador Android.
`InstalledUpgradeTest` confirmou versão instalada 0.1.1 e dois downloads concluídos com tamanhos e URIs preservados.

O teste de plataformas aceita `youtubeUrl`, `instagramUrl` e `tiktokUrl`. Sem URLs, é explicitamente ignorado. A prova curta do motor roda por padrão.

## Pendências para uma entrega final do plano

- Instalação e downloads em aparelho ARM64 limpo; Android 8/9 e SAF nas versões mínimas.
- Teste de atualização N → N+1 pelo fluxo público completo do GitHub e instalador do Android, com digest público real e a mesma chave release.
- Matriz extensa de falhas: disco cheio, queda/troca de rede, DRM, login, bloqueio regional, retomada por protocolo, desligamento e force-stop.
- Playlists com diferentes tipos de fonte, seleção de múltiplos itens e cache/parciais grandes.
- Dois downloads concorrentes com uma conversão; a versão atual executa um pipeline por vez.
- Inventário e fontes correspondentes de todas as bibliotecas nativas antes de publicar os APKs publicamente.

O aplicativo não deve ser anunciado como cumprindo todos os critérios de produção enquanto essas pendências não forem resolvidas.
