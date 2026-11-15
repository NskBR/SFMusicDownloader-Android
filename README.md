# SFMD — SFMusicDownloader

Aplicativo Android nativo em Kotlin e Jetpack Compose, com interface em português, abas Vídeo e Música e a identidade visual fornecida pelo proprietário do projeto.

## Implementado

- Consulta real por URL pelo yt-dlp; resolução e formato de vídeo; áudio original, MP3, M4A e Opus.
- Python, yt-dlp, FFmpeg, FFprobe, QuickJS e EJS incluídos no APK. A primeira execução extrai recursos locais, sem baixar ferramentas.
- Fila serial, Room, histórico, notificações, serviço `dataSync`, cancelamento de grupos de processos, pausa com parciais e recuperação explícita de tarefas interrompidas.
- Temporários privados, publicação no MediaStore e pasta alternativa pelo Storage Access Framework. Abrir e compartilhar por URI de conteúdo.
- Compartilhar do YouTube ou de outros apps diretamente para SFMD: escolher Música ou Vídeo, consultar automaticamente e ajustar nome, formato e qualidade antes de baixar.
- Pedido de permissões na primeira abertura; rascunho independente de cada aba; seleção dos primeiros 100 itens de playlists.
- Destino padrão na pasta **Download** do dispositivo. Configurações independentes para a pasta de vídeos e a pasta de músicas. O destino é capturado ao enfileirar a tarefa.
- Atualizador GitHub Releases: versões numéricas, ABI, streaming, tamanho, SHA-256, pacote, versionCode e assinatura. Instalação pelo Android, bloqueada enquanto houver tarefas ativas.

É uma versão inicial. Os resultados de teste e as pendências estão em [docs/VALIDACAO.md](docs/VALIDACAO.md). Um recurso implementado não implica que todos os sites ou todas as versões Android tenham sido validados.

## Abrir e compilar

Abra esta pasta no Android Studio. Instale Android SDK Platform 37 e Build Tools 36.0.0 ou compatíveis. Use o JDK do Android Studio (Gradle 9.3.1, AGP 9.1.1, Compose Compiler 2.2.10). O AGP fornece Kotlin integrado.

Crie `local.properties` com o caminho local do SDK, por exemplo:

```properties
sdk.dir=C\:/Users/SEU_USUARIO/AppData/Local/Android/Sdk
```

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Os APKs são separados por ABI: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` e `app-x86_64-debug.apk`. Android mínimo declarado: API 26. No Android 10+, Download usa MediaStore sem acesso geral ao armazenamento. No Android 8/9, o destino padrão pede a permissão de armazenamento; uma pasta SAF pode ser escolhida como alternativa.

## Prova do motor

O teste instrumentado faz consulta de um vídeo público curto, baixa vídeo com áudio, converte MP3 e publica ambos no MediaStore. Não inclui uma falsa implementação ou dados simulados.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para outra URL, passe `-Pandroid.testInstrumentationRunnerArguments.sourceUrl=https://...`. Redes e plataformas podem bloquear o teste. Os arquivos publicados são mantidos para conferência manual; apague-os depois pelo gerenciador de arquivos.

## Distribuição e assinatura

Leia [docs/RELEASE.md](docs/RELEASE.md). Nunca publique o keystore ou a senha. Debug e release usam assinaturas diferentes; uma versão release não atualiza uma instalação debug. Use a mesma chave release em todas as futuras versões.

O código do SFMD é disponibilizado sob GPLv3, acompanhando o wrapper Android e os componentes FFmpeg. Veja [THIRD_PARTY.md](THIRD_PARTY.md). Mantenha o código-fonte correspondente e os avisos disponíveis junto ao APK. Uma distribuição proprietária exige rever a escolha dessas dependências antes de publicar.

## Limites

Spotify, Apple Music e Deezer não são tratados como fonte de download direto. Não há busca automática de correspondências em outra fonte. Conteúdo privado, restrições de idade/região, login, DRM e mudanças dos sites podem impedir um download. A fonte real é exibida no resultado.

A fila usa um único pipeline nesta versão para não executar conversões simultâneas. A proposta de dois downloads e uma conversão exige separar essas etapas antes de aumentar o paralelismo. Retomada é uma nova execução compatível com os parciais; nunca é garantida para toda fonte.
