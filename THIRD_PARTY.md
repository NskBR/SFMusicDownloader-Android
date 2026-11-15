# Componentes e distribuição

O aplicativo se apoia no wrapper youtubedl-android 0.18.1 (GPLv3). O arquivo LICENSE acompanha essa escolha. Os binários FFmpeg podem incluir componentes GPL e LGPL. Distribuir o APK exige os avisos e o código-fonte correspondente exigidos pelas licenças; a simples existência de links genéricos não substitui essa obrigação.

| Componente | Versão fixada | Origem | Licença / observação |
|---|---|---|---|
| youtubedl-android library/common | 0.18.1 | Maven Central, `io.github.junkfood02.youtubedl-android` | GPLv3; fontes do wrapper em https://github.com/yausername/youtubedl-android |
| CPython | 3.12.11 | `library-0.18.1.aar`, por ABI | PSF; distribuição embarcada e bibliotecas precisam de inventário completo |
| FFmpeg / FFprobe | 9.0.2 | Maven Central, `io.github.deniscerri:ytdlnis_packages.ffmpeg:9.0.2` | GPLv3; https://github.com/deniscerri/ytdlnis-packages; substitui o FFmpeg 0.18.1 que falhou em 16 KB |
| QuickJS | 2025-04-26 | `libqjs.so` da biblioteca 0.18.1, por ABI | MIT |
| yt-dlp | 2026.10.26 | https://pypi.org/project/yt-dlp/2026.10.26/ | Unlicense; wheel original embarcado |
| yt-dlp-ejs | 0.8.0 | https://pypi.org/project/yt-dlp-ejs/0.8.0/ | Unlicense; wheel original embarcado |
| AndroidX / Compose / Room / DataStore | `app/build.gradle.kts` | Google Maven | Apache-2.0, salvo avisos específicos |
| Kotlin / Coroutines | build e dependências | Maven Central | Apache-2.0 |
| OkHttp | 4.12.0 | Maven Central | Apache-2.0 |
| Coil | 2.7.0 | Maven Central | Apache-2.0 |
| Logo SF | imagem fornecida pelo usuário | anexo local | Direitos e autorização de uso sob responsabilidade do proprietário |

As duas wheels estão incluídas em `app/src/main/assets/engine/`, com licença e metadados originais. Nenhum componente executável é baixado na primeira inicialização. A cópia antiga de yt-dlp incluída no AAR não é executada: o adaptador usa `python -m yt_dlp` com PYTHONPATH apontando para os módulos fixados pelo SFMD.

## Hashes das entradas

SHA-256:

```text
a05c5b5d8562f7e8a62f5334348b406af4930d56d3a65f8021d14af0e22339ae  yt_dlp-2026.10.26-py3-none-any.whl
79300e5fca7f937a1eeede11f0456862c1b41107ce1d726871e0207424f4bdb4  yt_dlp_ejs-0.8.0-py3-none-any.whl
579b5fb480892b1abc2b218c2089699d52759cc8d7ba256bf876453f0365faef  library-0.18.1.aar
e0c0530bdbdf203e1598e900085d3582e27360731872872f3a67f23a9b24c0b5  common-0.18.1.aar
52b1c861554c2f21ad9860a1e6583cb34b86f20a279a5516387d7470b5b9cc98  ytdlnis_packages.ffmpeg-9.0.2.aar
```

Código Android consultado durante a implementação: commit `d725d5c9a18c3a99a13ee0308bf78275dc310760` de `yausername/youtubedl-android`. O empacotamento é consumido pelo Maven, sem copiar telas de outro downloader. Fontes de referência: `BUILD_PYTHON.md` e `BUILD_FFMPEG.md` do wrapper e os projetos CPython, QuickJS e Termux mencionados neles.

Pendente antes de publicar uma release pública: inventário detalhado das bibliotecas dentro dos dois ZIPs nativos e arquivamento dos fontes correspondentes e configurações de build de cada binário. O APK entregue nesta etapa é para avaliação e validação local.
