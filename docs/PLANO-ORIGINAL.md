# Plano de implementação — downloader Android de vídeo e música

Documento de passagem para outro chat e outro desenvolvedor. Preparado em 15/11/2026. Nome do produto ainda será definido. Criar um projeto próprio para este aplicativo.

## 1. Resultado esperado

Entregar um APK Android instalável com interface em português, duas abas superiores lado a lado, **Vídeo** e **Música**, consulta por URL e downloads executados no próprio celular.

O APK deve conter o motor de extração, Python, FFmpeg, FFprobe, runtime JavaScript e componentes necessários. O usuário instala apenas o aplicativo. O primeiro download deve funcionar sem instalar Termux, Python, FFmpeg, plugins, outro APK ou ferramentas auxiliares. A instalação inicial pode preparar automaticamente os recursos que já estão dentro do APK.

Premissas propostas para a primeira implementação

- Android 8.0 ou superior, sujeito à compatibilidade real das dependências escolhidas.
- APK principal ARM64, para celulares modernos. APK separado x86_64 para testes em emulador. Só anunciar outras arquiteturas depois de testá-las.
- Processamento e histórico locais. As consultas e os downloads precisam de internet.
- Distribuição inicial por APK assinado e GitHub Releases.
- Referência funcional no SFDownloader. Consultar seu código antes de afirmar que uma integração de música tem o mesmo comportamento.

## 2. Compatibilidade e limites

O objetivo é atender URLs de todas as plataformas efetivamente compatíveis com os extratores instalados. Começar a validação por YouTube, Shorts, Instagram e Facebook. Tratar suporte por tipo de conteúdo e por teste real, incluindo vídeo público, reel, postagem com vários itens e playlist.

Uma plataforma listada pelo yt-dlp pode deixar de funcionar quando o site muda. Login, conteúdo privado, idade, região e bloqueios temporários afetam a disponibilidade. A interface deve diferenciar esses casos. [Lista oficial de sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)

Na aba Música, distinguir três situações

1. URL com áudio acessível e suportado, que permite download da própria fonte.
2. URL de vídeo da qual se pode extrair o áudio.
3. URL que disponibiliza somente metadados de uma faixa, álbum ou playlist. Essa integração exige um provedor próprio e uma decisão explícita sobre buscar outra fonte. Mostrar a fonte real e pedir confirmação quando houver correspondência aproximada.

Não prometer download direto de todo o catálogo de Spotify, Apple Music, Deezer ou qualquer outro serviço apenas porque o usuário colou uma URL. Conteúdo com DRM deve receber um resultado claro de incompatibilidade. Metadados, prévias e áudio completo são capacidades diferentes. [Extratores explicitamente incompatíveis com DRM](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/unsupported.py)

## 3. Tecnologias propostas

| Área | Tecnologia e uso |
| --- | --- |
| Linguagem | Kotlin |
| Interface | Jetpack Compose e Material 3, com identidade visual própria |
| Estado e concorrência | ViewModel, Kotlin Coroutines e StateFlow |
| Extração e download | yt-dlp com integração Android |
| Integração inicial a avaliar | youtubedl-android, publicado como io.github.junkfood02.youtubedl-android, encapsulado em um adaptador próprio |
| Ambiente Python | CPython embarcado compatível com o yt-dlp fixado no projeto |
| Processamento | FFmpeg e FFprobe compilados para Android e incluídos no APK |
| JavaScript | QuickJS executável compatível com Android, mais yt-dlp-ejs embarcado |
| Rede do aplicativo | OkHttp para metadados auxiliares e atualização do aplicativo; o motor mantém sua própria camada de rede |
| JSON | kotlinx.serialization para os contratos internos e as respostas normalizadas |
| Banco local | Room para fila, histórico e escolhas de download |
| Preferências | DataStore |
| Imagens e capas | Coil |
| Prévia local | AndroidX Media3, quando houver mídia reproduzível disponível |
| Arquivos | MediaStore, Storage Access Framework e FileProvider |
| Execução longa | Coordenador de downloads com integração às APIs Android de transferência iniciada pelo usuário e serviço em primeiro plano conforme versão e tarefa |
| Build | Android Studio, Gradle Wrapper, Kotlin DSL, Android SDK e NDK quando necessário para os binários |
| Verificação | JUnit, testes de integração do motor, testes Compose e testes instrumentados em Android |
| Distribuição | APK release assinado, GitHub Actions e GitHub Releases |

A biblioteca youtubedl-android demonstra o empacotamento Android e a integração com FFmpeg. Os exemplos de README consultados também citam Python antigo. A IA deve conferir os binários da versão escolhida e fixar versões compatíveis. O primeiro marco é uma prova funcional do motor embarcado. Se o wrapper não atender aos requisitos atuais, adaptar a integração ou escolher um fork mantido, registrando a decisão e as licenças. [Biblioteca Android](https://github.com/yausername/youtubedl-android)

Na documentação consultada, yt-dlp suporta Python 3.11+ e recomenda FFmpeg, FFprobe, yt-dlp-ejs e runtime JavaScript. Usar Python 3.13 como candidato inicial, condicionado à biblioteca Android escolhida. [Dependências oficiais](https://github.com/yt-dlp/yt-dlp#dependencies)

QuickJS deve ser empacotado como executável que o yt-dlp consegue iniciar. Uma biblioteca JavaScript apenas dentro da interface não atende automaticamente a essa integração. Configurar explicitamente o caminho com --js-runtimes e fornecer os scripts EJS compatíveis. [Guia oficial EJS](https://github.com/yt-dlp/yt-dlp/wiki/EJS)

## 4. Estrutura da interface

Barra superior com nome do app, acesso discreto às configurações e abas **Vídeo | Música** de mesma largura. Manter o rascunho e a seleção de cada aba ao alternar.

Cada aba deve oferecer

- Campo de URL com botão Colar e ação Consultar.
- Validação de endereço e mensagem de erro próxima do campo, sem deslocar o conteúdo.
- Loading animado com estado “Consultando link…” e opção de cancelar.
- Card de resultado com miniatura ou capa, título, origem, autor, duração e opções disponíveis.
- Botão Baixar habilitado quando a consulta tiver resultado válido.
- Lista compacta de tarefas com progresso, velocidade, tamanho, tempo restante quando conhecido e ações possíveis.
- Histórico filtrado pela aba atual, com acesso ao arquivo e compartilhamento.

Vídeo

- Oferecer apenas resoluções, taxas de quadros, áudio e formatos realmente disponíveis.
- Permitir melhor qualidade automática e escolha manual.
- Preservar formato vertical para Shorts e reels nas prévias.
- Exibir tamanho estimado como estimativa quando o servidor não informar o total.
- Para playlists, permitir selecionar itens antes de enfileirar e mostrar progresso por item.

Música

- Permitir áudio original, M4A, Opus e MP3 quando o motor e os codecs embarcados permitirem.
- Oferecer bitrate apenas nas conversões em que ele se aplica.
- Explicar que converter para FLAC não melhora a qualidade de uma fonte com perdas.
- Salvar título, artista, álbum e capa quando os dados estiverem disponíveis.
- Mostrar a origem real do áudio. Nunca informar que baixou de um serviço se o arquivo veio de outro.

Tema escuro inicial, texto legível, alvos de toque confortáveis, cantos arredondados e animações discretas. Reduzir movimento conforme configuração do sistema. Os estados vazios, erros e carregamento devem ocupar áreas estáveis para evitar saltos de layout.

## 5. Fluxo e arquitetura

Fluxo principal

`Colar ou compartilhar URL → consultar → escolher mídia e qualidade → enfileirar → baixar → mesclar ou converter → salvar → abrir ou compartilhar`.

Pacotes sugeridos

```text
app/
core/model/
core/database/
core/preferences/
core/files/
core/network/
core/designsystem/
engine/ytdlp/
engine/ffmpeg/
features/video/
features/music/
features/downloads/
features/settings/
```

Separar consulta, fila, processamento e armazenamento. A interface observa estados e chama casos de uso. O adaptador do motor transforma resultados e callbacks em contratos próprios, evitando espalhar comandos do yt-dlp nas telas.

Contratos mínimos

- `MediaInfo` com URL de origem, plataforma, título, autor, duração, capa, formatos e itens de playlist.
- `DownloadRequest` com ID, origem, aba, formato selecionado, destino e opções de áudio.
- `DownloadTask` com ID estável, estado, bytes, velocidade, etapa, caminhos temporários e URI final.
- `DownloadEngine` com consultar, baixar, cancelar e capacidades de retomada.
- `MusicProvider` com capacidades explícitas de metadados e obtenção de áudio.

Estados da tarefa

`Na fila → Preparando → Baixando → Processando → Salvando → Concluído`.

Também prever pausado, cancelado, interrompido e falhou. A etapa Processando não deve exibir 100% de download como se o arquivo já estivesse pronto.

Limite inicial proposto de dois downloads simultâneos e uma conversão por vez, configurável. A fila deve suportar várias tarefas e evitar disparos duplicados por cliques repetidos.

## 6. Downloads, persistência e armazenamento

Persistir o pedido antes de iniciar. Identificar cada execução pelo ID da tarefa. Cancelar deve encerrar os processos filhos associados e atualizar o banco.

Retomada depende do protocolo, arquivo parcial e suporte do motor. Ao pausar por encerramento do processo, preservar parciais e retomar mediante nova execução compatível. Não apresentar garantia de retomar todo site ou continuar durante encerramento forçado do app.

Consultar as APIs atuais do Android antes de implementar tarefas longas. Avaliar User-Initiated Data Transfer Job em versões compatíveis, serviço em primeiro plano nos casos apropriados e WorkManager para manutenção agendada. Modelar download e conversão como etapas distintas, respeitando limites de execução e notificações. [Guia Android para transferências](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options)

Salvar temporários em diretório privado do app. Após mesclar ou converter, publicar o resultado no MediaStore ou no destino SAF escolhido, sem expor arquivos incompletos como concluídos. Persistir a permissão de URI quando aplicável. Conferir espaço, limpar temporários de tarefas finalizadas e permitir escolher o destino pelas configurações.

Receber URLs pelo compartilhamento do Android via ACTION_SEND. Abrir e compartilhar o resultado usando URI de conteúdo e permissões temporárias. Pedir somente as permissões necessárias à operação e à versão do Android.

## 7. APK completo e manutenção

O build deve trazer todos os recursos necessários dentro do APK, incluindo certificados e bibliotecas Python exigidas pelos extratores selecionados. Registrar versão e origem de cada componente, ABI, licença e hash. Validar execução de binários nativos no Android e compatibilidade com páginas de memória de 16 KB.

Não gerar apenas um instalador que baixa Python, FFmpeg ou runtime na primeira execução. O tamanho final deve ser medido no protótipo e informado ao usuário, pois os motores embarcados aumentam o APK.

Entregar um APK ARM64 completo. Um APK universal pode ser gerado como opção, com custo de tamanho maior. Manter a mesma chave de assinatura entre atualizações para preservar a instalação e os dados. Guardar keystore e senhas fora do repositório.

Manter motores atualizáveis pela própria publicação do APK desde o início. Atualização interna de componentes pode ser adicionada depois, com integridade, compatibilidade e rollback. O APK precisa continuar incluindo uma versão funcional de fábrica. Verificar as regras de distribuição antes de adotar download de código executável em qualquer loja.

Os sites mudam e haverá manutenção recorrente dos extratores. Um APK completo remove instalações auxiliares, mas continua dependendo dos serviços de origem e de suas mudanças.

## 7.1. Atualização do aplicativo pelo GitHub Releases

Implementar desde a primeira entrega um atualizador dentro do Android, semelhante ao SFLink. O aplicativo consulta a release pública, apresenta a nova versão, baixa e verifica o APK e abre a confirmação de instalação do Android. A instalação deve atualizar o aplicativo existente preservando preferências, fila, histórico e arquivos.

### Experiência do usuário

Em **Configurações → Atualizações**, mostrar versão instalada, estado da consulta, versão disponível, notas da versão e ações **Verificar atualizações**, **Baixar atualização** e **Instalar atualização**. Mostrar progresso e opção de cancelar o download. Quando houver atualização disponível, apresentar também um aviso discreto com acesso a essa tela.

Consultar uma vez ao abrir o aplicativo, de forma assíncrona. Oferecer consulta manual e registrar a última verificação para evitar consultas repetidas em recriações da tela. Falhas de conexão ou limites do GitHub devem permitir tentar novamente depois. Download e instalação dependem de ação do usuário.

Fluxo previsto

`Consultar release → informar nova versão → usuário toca em Baixar → baixar APK → verificar integridade e identidade → usuário toca em Instalar → autorizar esta fonte, se necessário → confirmação do Android → reabrir o aplicativo atualizado`.

O Android exige autorização para instalar aplicativos desta fonte e confirmação da instalação em aparelhos comuns. Explicar essas etapas na interface e tratar recusa ou cancelamento como estados recuperáveis. [Permissão de instalação por fonte](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())

### Organização e estados

Criar `features/updates/` com tela Compose e ViewModel, e `core/updates/` com repositório, cliente GitHub, download e validação do APK. Usar Coroutines e StateFlow conforme a arquitetura do projeto. Consultar a implementação Android do SFLink como referência, adaptando identificador, assinatura, arquivos, versões e execução longa deste aplicativo.

Modelar estados de consultando, atualizado, atualização disponível, baixando, verificando, pronto para instalar, aguardando autorização do Android e erro. Impedir consultas e downloads duplicados e preservar o arquivo validado ao abrir as configurações de autorização. Após encerramento do processo, verificar novamente qualquer APK reaproveitado.

Contrato `UpdateOffer` com versão, tag, notas, nome do asset, URL, tamanho, SHA-256 e ABI. Configurar o repositório público do novo aplicativo no build, sem credenciais GitHub embutidas.

### Contrato da release

Consultar `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`. Aceitar uma release estável publicada com tag `vMAJOR.MINOR.PATCH` e versão superior à instalada. Comparar componentes numéricos. Confirmar também que o `versionCode` do APK é superior ao instalado.

Definir nomes estáveis após escolher o nome do produto, por exemplo `NomeDoApp_1.2.0_arm64-v8a.apk` e `NomeDoApp_1.2.0_x86_64.apk`. Selecionar o asset compatível com a instalação e com as ABIs suportadas pelo aparelho. Quando faltar o APK adequado, informar que a versão está indisponível para esse dispositivo.

Usar `browser_download_url`, `size` e `digest` do asset. Exigir SHA-256 válido e tratar digest ausente como publicação incompleta. Validar a URL inicial contra o repositório e a tag esperados, exigir HTTPS e tratar os redirecionamentos de download do GitHub com limite. Publicar também `SHA256SUMS.txt` para conferência manual. [Contrato de assets do GitHub](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)

### Download e verificação

Baixar com OkHttp em streaming para um arquivo `.part` em diretório privado. Definir limites de tempo, tamanho e redirecionamentos, conferir espaço e permitir cancelamento. Dimensionar o limite máximo para o APK completo medido no protótipo, incluindo seus motores embarcados.

Conferir bytes recebidos e SHA-256 durante a leitura. Antes de habilitar instalação, inspecionar o pacote com PackageManager e verificar `applicationId`, `versionName`, `versionCode` e assinatura correspondente à instalação atual. Para Android 8, prever a leitura de assinaturas compatível com API 26/27. Para API 28+, usar SigningInfo. Uma eventual rotação de chave exige implementação e testes específicos de linhagem de assinatura.

Manter a mesma chave release entre versões. Se a assinatura diferir, mostrar erro e manter a instalação atual. Revalidar o arquivo antes de entregá-lo ao instalador. Arquivos parciais, corrompidos ou cancelados devem ser limpos.

### Instalação e tarefas em andamento

Declarar `REQUEST_INSTALL_PACKAGES` para a distribuição por APK. Conferir `canRequestPackageInstalls()` e, quando necessário, abrir `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` com a URI do pacote. Ao voltar, permitir tentar instalar novamente.

Disponibilizar somente o APK verificado por FileProvider, usando URI `content://`, MIME `application/vnd.android.package-archive` e permissão temporária de leitura. Abrir o instalador do sistema. Retornar à interface sem afirmar que houve atualização apenas porque o instalador abriu. [Ações de abertura e instalação](https://developer.android.com/reference/android/content/Intent)

Bloquear o início da instalação enquanto houver consultas, downloads, conversões ou salvamentos ativos. Revalidar essa condição no momento da instalação e impedir que novas tarefas comecem durante a entrega ao instalador. Persistir a fila antes dessa etapa e recuperar tarefas interrompidas na próxima abertura.

O download do APK deve obedecer ao modelo de execução longa definido na seção 6. Definir e testar seu comportamento ao apagar a tela ou sair do app.

### Publicação e validação

Gerar o APK completo em modo release e assinar com a chave protegida. Publicar inicialmente uma release em rascunho com os APKs previstos, notas e hashes. Conferir versão, ABI, assinatura, tamanho e digest antes de torná-la pública. Confirmar que a API pública e o download anônimo oferecem os mesmos arquivos. O atualizador deve funcionar com releases produzidas localmente ou por CI.

Testar uma atualização real de N para N+1 com a mesma assinatura, preservando preferências, histórico e fila. Cobrir rede interrompida, falta de espaço, cancelamento, digest inválido, assinatura diferente, pacote errado, versão inferior, ABI incompatível, ausência de APK, autorização recusada e instalação cancelada. Verificar que tarefas ativas impedem instalação e que o usuário consegue repetir uma tentativa.

## 8. Ordem de implementação

1. **Prova do motor.** Gerar APK ARM64 com Python compatível, yt-dlp, FFmpeg, FFprobe, QuickJS e EJS. Em aparelho físico limpo, consultar YouTube e produzir um vídeo com áudio e um arquivo de áudio. Conferir ausência de dependências externas.
2. **Fluxo Vídeo.** Criar as duas abas e completar URL, consulta, escolha de qualidade, download e salvamento na aba Vídeo. Testar também Instagram e Facebook e registrar o que funcionou.
3. **Fila e ciclo de vida.** Persistência, notificações, execução em segundo plano, cancelamento, retomada possível e recuperação após interrupção.
4. **Fluxo Música.** Áudio de URLs suportadas, formatos, metadados e capa. Depois adicionar os provedores específicos que forem validados.
5. **Polimento.** Compartilhamento Android, playlists, configurações, acessibilidade, erros claros e adaptação a telas menores.
6. **Atualizador Android.** Implementar a seção 7.1 e testar atualização assinada de N para N+1 com preservação dos dados.
7. **Entrega.** Testes, APK release assinado, hashes, documentação de build e matriz de compatibilidade testada.

## 9. Critérios para chamar o app de pronto

- Instalação limpa em celular ARM64 com somente o APK entregue.
- Consulta, download de vídeo com áudio e extração de áudio realmente executados.
- Motor JavaScript e EJS encontrados pelo yt-dlp sem configuração do usuário.
- Vídeos verticais, nomes longos e URLs inválidas tratados corretamente.
- Erros de rede, mídia indisponível, autenticação exigida e falta de espaço diferenciados.
- Fila e histórico persistem após fechar e reabrir a interface.
- Cancelamento interrompe o trabalho correto e não deixa tarefa presa.
- Resultado final aparece na pasta escolhida e pode ser aberto e compartilhado.
- Teste de download com tela apagada, troca de rede e interrupção do processo, registrando as limitações observadas.
- Atualização pelo próprio app baixa o APK do GitHub, valida tamanho, SHA-256, pacote, versão e assinatura e abre a confirmação do Android.
- Atualização do APK assinado preserva configurações, histórico e fila.
- Instalação aguarda tarefas ativas e permite repetir após recusa de autorização ou cancelamento.
- Relatório distingue plataformas testadas de plataformas apenas listadas pelo extrator.

## 10. Entregáveis para o outro chat

Projeto Android completo, APK release ARM64 assinado, SHA-256, documentação de build e publicação no GitHub Releases, atualizador integrado, procedimento de proteção da chave de assinatura, evidência do teste N para N+1, catálogo de dependências e licenças, matriz de testes por plataforma e relatório de pendências.

A IA deve iniciar pela prova do motor, fixar as versões que realmente funcionaram e implementar o fluxo completo em etapas. Telas com dados simulados podem ajudar no design, mas a entrega exige os downloads reais previstos nos critérios acima.

Referência adicional para estudar integração Android e arquitetura de um downloader existente, respeitando suas licenças ao reutilizar código. [YTDLnis](https://github.com/deniscerri/ytdlnis)