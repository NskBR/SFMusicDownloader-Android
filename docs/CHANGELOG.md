# 0.1.4

- Reduz o tempo em Preparando reutilizando os dados completos de uma consulta feita nos últimos dois minutos, sem extrair novamente a página antes de baixar.
- Se o link de mídia tiver expirado, renova a consulta automaticamente. Playlists e consultas sem dados completos mantêm o fluxo normal.
- Sinaliza a etapa Processando durante a conversão e a finalização do arquivo.
- Validação no emulador: início da transferência de um vídeo passou de 11,0 segundos para 0,5 segundo; MP3 iniciou em 0,6 segundo. Tempos variam conforme a plataforma e a rede.
- Teste Android confirmou recuperação de link expirado, qualidade de vídeo, áudio, título personalizado e limpeza dos dados temporários. Testes unitários e Lint passaram.

Atualização compatível por cima da 0.1.3, preservando os dados.
# 0.1.3

- Mostra bytes, percentual quando o tamanho é conhecido e velocidade quando o servidor fornece dados suficientes.
- Mantém o estado Preparando até o início da transferência.
- Baixa até quatro fragmentos em paralelo nas fontes compatíveis.
- Teste confirmado pelo usuário no celular após instalar a 0.1.3 estável por cima da versão de teste. A porcentagem e a velocidade apareceram durante o download.

Instale por cima da versão anterior para preservar seus dados. Quem instalou a 0.1.3 de teste pode atualizar abrindo o APK estável diretamente.

# 0.1.2

- A consulta principal de atualização usa `releases/latest/download/update.json`. A API pública é consultada somente se o arquivo estiver indisponível.
- Cache de cinco minutos para consultas automáticas; o botão Verificar atualizações força uma consulta nova. Não são incluídos tokens ou credenciais no aplicativo.
- O aviso verde “Atualização disponível” aparece ao lado de SFMD e abre as configurações de atualização.
- O build release local passa a gerar `update.json` a partir dos APKs assinados, com tamanhos e hashes reais. É necessário anexar esse arquivo a cada release estável.
- As verificações de pacote, ABI, versão, SHA-256 e mesma assinatura continuam obrigatórias. O versionCode do manifesto também é confrontado com o APK.

# 0.1.1

- Ao enfileirar, fecha o resultado e limpa o campo de URL; a tarefa permanece na lista de downloads.
- Desabilita o botão durante o enfileiramento e impede cliques repetidos de criar tarefas concorrentes da mesma mídia.
- Uma mídia concluída no histórico exige confirmação para baixar novamente. Cancelar mantém a prévia e as opções. Música e vídeo são comparados separadamente, reconhecendo URLs equivalentes do YouTube.
- O nome escolhido também é gravado no título interno do arquivo. O teste lê o título do MP3 via MediaMetadataRetriever, incluindo acentos e o caractere `%`.
- Cache de até 16 consultas recentes por cinco minutos, compartilhado entre Vídeo e Música, e cache interno do yt-dlp habilitado. Somente resultados bem-sucedidos entram no cache.
- Consulta usa timeout de socket de 15 segundos e uma repetição para rede/extrator; downloads mantêm limites próprios. Vídeos individuais do YouTube não expandem playlists associadas. A verificação prévia de todos os formatos foi desabilitada; o download continua validando os formatos efetivamente usados.

O ganho imediato do cache se aplica a consultas repetidas. A primeira consulta continua dependendo da resposta da plataforma e da rede.

Validação local em 15/11/2026: build e Lint passaram; testes de tela confirmaram proteção de cliques e confirmação de duplicidade; a prova real baixou vídeo e MP3 e confirmou o título interno `Minha música SFMD 100%`. Primeira consulta: 12.913 ms; repetição em cache: abaixo da resolução de 1 ms do relógio usado.
