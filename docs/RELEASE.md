# Build e publicação

O aplicativo ainda precisa completar os testes pendentes em VALIDACAO.md antes de ser chamado de pronto.

## Chave

Crie uma chave release com alias `sfmd`, senha forte e validade suficiente. Armazene fora do repositório. Faça cópia segura offline da chave e da senha. A perda da chave impede atualizar as instalações existentes. A senha pode ser fornecida ao script em `SFMD_STORE_PASSWORD`; não escreva seu valor em comandos salvos, logs ou arquivos versionados.

```powershell
.\scripts\build-release.ps1 -KeyStore 'CAMINHO_FORA_DO_REPOSITORIO\sfmd-release.jks' -Version 0.1.0 -VersionCode 1
```

O script gera `release/SFMD_0.1.0_arm64-v8a.apk`, `release/SFMD_0.1.0_x86_64.apk` e `release/SHA256SUMS.txt`. Nunca incluir material de assinatura na pasta release.

## Contrato GitHub Releases

Repositório: `NskBR/SFMusicDownloader-Android`. Tag estável `vMAJOR.MINOR.PATCH`, sem prerelease. Cada versão deve aumentar também `versionCode`.

1. Compilar, assinar e verificar os dois APKs.
2. Criar release em rascunho com os nomes exatos previstos pelo atualizador, notas e SHA256SUMS.txt.
3. Associar o commit do código-fonte correspondente, inventário de componentes, licenças e instruções para obter os fontes dos binários.
4. Conferir assinatura, versão, tamanho e arquitetura. Não publicar chaves ou senhas.
5. Publicar somente depois da revisão. Verificar por acesso anônimo a API `/releases/latest` e os downloads.
6. Exigir `digest` SHA-256 na resposta de cada asset. Sem digest o app considera a publicação incompleta.
7. Testar N → N+1, preservando DataStore, Room, parciais e URIs finais. Conferir bloqueio da instalação durante tarefas.

O atualizador aceita somente a mesma assinatura atual. Rotação de chave não está implementada. O usuário sempre confirma a instalação no Android. Abrir o instalador não é evidência de instalação concluída.

## Execução longa

Os downloads de mídia e de atualização iniciados pelo usuário usam um serviço em primeiro plano do tipo dataSync, com notificação e wake lock limitado. A versão respeita o encerramento por timeout do Android e registra interrupções recuperáveis. Não promete sobreviver a force-stop. A estratégia UIDT para versões recentes continua como evolução da implementação.
