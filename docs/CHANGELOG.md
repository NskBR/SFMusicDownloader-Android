# 0.1.1

- Ao enfileirar, fecha o resultado e limpa o campo de URL; a tarefa permanece na lista de downloads.
- Desabilita o botão durante o enfileiramento e impede cliques repetidos de criar tarefas concorrentes da mesma mídia.
- Uma mídia concluída no histórico exige confirmação para baixar novamente. Cancelar mantém a prévia e as opções. Música e vídeo são comparados separadamente, reconhecendo URLs equivalentes do YouTube.
- O nome escolhido também é gravado no título interno do arquivo. O teste lê o título do MP3 via MediaMetadataRetriever, incluindo acentos e o caractere `%`.
- Cache de até 16 consultas recentes por cinco minutos, compartilhado entre Vídeo e Música, e cache interno do yt-dlp habilitado. Somente resultados bem-sucedidos entram no cache.
- Consulta usa timeout de socket de 15 segundos e uma repetição para rede/extrator; downloads mantêm limites próprios. Vídeos individuais do YouTube não expandem playlists associadas. A verificação prévia de todos os formatos foi desabilitada; o download continua validando os formatos efetivamente usados.

O ganho imediato do cache se aplica a consultas repetidas. A primeira consulta continua dependendo da resposta da plataforma e da rede.

Validação local em 15/11/2026: build e Lint passaram; testes de tela confirmaram proteção de cliques e confirmação de duplicidade; a prova real baixou vídeo e MP3 e confirmou o título interno `Minha música SFMD 100%`. Primeira consulta: 12.913 ms; repetição em cache: abaixo da resolução de 1 ms do relógio usado.
