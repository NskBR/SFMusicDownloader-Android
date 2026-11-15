# Atualizações sem depender da cota da API

A partir da 0.1.2, a consulta principal lê:

`https://github.com/NskBR/SFMusicDownloader-Android/releases/latest/download/update.json`

Esse é um download público de asset, sem chamada à API REST e sem token. As versões já instaladas 0.1.0/0.1.1 continuam usando o mecanismo antigo até serem atualizadas; publicar o JSON não altera o código desses APKs.

O cliente segue até cinco redirecionamentos, exige HTTPS e hosts de distribuição do GitHub, limita a resposta a 256 KB e valida schema, pacote, release estável, versão, versionCode, ABI, URL, tamanho e SHA-256. Um manifesto inválido gera erro; não é ocultado pela alternativa. Falta do arquivo ou falha de transporte permite tentar a API. Limite da API não é apresentado como aplicativo atualizado.

Respostas válidas ficam em memória por cinco minutos. O botão Verificar atualizações ignora esse cache. O aviso verde usa a oferta de atualização validada e abre Configurações. A ausência de atualização oculta o aviso.

## Publicação

O script `scripts/build-release.ps1` gera `release/update.json` depois de assinar e verificar os APKs. Envie-o junto dos dois APKs e de `SHA256SUMS.txt` à release em rascunho, confira os arquivos e depois publique a release estável. Uma release nova sem o asset fará o cliente tentar a API novamente.

É possível gerar só o JSON, sem build, usando APKs que já existem:

```powershell
.\scripts\new-update-manifest.ps1 -Version 0.1.1 -VersionCode 2 -ApkDirectory .\release
```

O arquivo usa `schema_version: 1`, `package_name`, `version_code`, `tag_name`, `draft: false`, `prerelease: false`, `body` e `assets`. Cada asset tem nome estável, tamanho em bytes, digest `sha256:...` e URL pública que corresponde ao repositório e à tag. O script calcula tamanho e digest; não escreva hashes manualmente.

O manifesto ajuda a descobrir a versão; não substitui a validação do APK. Antes de instalar, o aplicativo verifica novamente hash, pacote, versão e a mesma assinatura da instalação atual. O Android continua exigindo a confirmação do usuário.
