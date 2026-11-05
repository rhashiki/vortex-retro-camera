# Vortex Retro Camera

**Retro Camera by Vortex** · Android nativo · Kotlin · CameraX/Camera2 · offline

Um aplicativo de fotografia com referências a filmes analógicos e câmeras digitais compactas de 1999–2006. Os perfis são simulações originais, não produtos oficiais ou reproduções calibradas de marcas.

## Estado

**v0.1.0-alpha / M1 (primeiro milestone; APK ainda sem validação).** Este pacote começa a implementação real da câmera: captura, presets fotográficos, interface e controles MANUAL_SENSOR experimentais. **Panorama e Vídeo estão presentes apenas na navegação e propositalmente desabilitados** até possuírem pipeline de processamento de filme. A prévia em tempo real tem uma tonalização apenas indicativa; a imagem salva recebe o filtro completo na CPU.

## Requisitos

- Android Studio compatível com AGP 8.13.2, JDK 17 e Android SDK 36.
- Gradle 8.13 (o GitHub Actions instala essa versão automaticamente).
- Android 10+ (API 29); câmera traseira ou frontal.

## Executar

1. Abra a pasta do projeto no Android Studio.
2. Aguarde a sincronização Gradle, instale Android SDK 36 quando solicitado.
3. Execute `app` em dispositivo Android físico e conceda acesso à câmera.
4. Escolha o filme e toque no obturador. O arquivo aparece em **Fotos/Vortex Retro Camera** na galeria.

## Gerar APK pelo GitHub

Após subir o conteúdo para o repositório, acesse **Actions → Android Debug APK → Run workflow**. Ao concluir, abra a execução, **Artifacts → vortex-retro-camera-debug**, baixe e extraia o ZIP para obter `app-debug.apk`. O APK de debug pode ser instalado para testes (não é assinatura de produção).

Também é possível gerar com `gradle :app:assembleDebug` em um ambiente com SDK configurado.

## Princípios de produto

- Sem PWA, WebView, backend, conta, anúncios ou rede.
- Fotos processadas no dispositivo, sem upload.
- Foto, Panorama, Pro e Vídeo devem futuramente compartilhar o mesmo Film Engine.
- Respeitar capacidades físicas da câmera e não inventar hardware ausente.
- Não confundir filme analógico com sensor CCD digital.

Veja [docs/ROADMAP.md](docs/ROADMAP.md) para o estágio real de cada função.

## Contribuição e status de CI

A branch `feat/android-m1-foundation` contém a proposta M1 para revisão; `main` permanece intacta até merge. GitHub Actions compilará o APK debug ao receber um pull request. O código não foi testado em dispositivo físico; consulte o resultado do CI antes de instalar.

**Marca:** a arte Vortex foi fornecida pelo proprietário. Os nomes Kodak, Fujifilm e Sony identificam inspirações de simulação e não implicam afiliação comercial.
