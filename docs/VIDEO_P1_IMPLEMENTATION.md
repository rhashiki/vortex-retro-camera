# Vortex Camera — Entrega P1: Foto + Vídeo nativos (v0.7.0-alpha)

## Entregue nesta branch
- Dependência `androidx.camera:camera-video:1.6.2` (mesma versão das demais CameraX).
- Modo **Foto** continua no `ImageCapture` e no `FilmProcessor` existente, com OEM Night e MediaStore JPEG.
- Modo **Vídeo** usa `Preview + VideoCapture<Recorder>` em um **binding dedicado**. Não força `ImageAnalysis` nem `ImageCapture` simultaneamente, compatível com mais câmeras.
- `VideoRecordingController.kt` salva `Movies/Vortex Retro Camera/Vortex_*.mp4` via MediaStore; escolhe HD como alvo, com fallback de qualidade quando não suportado.
- Estados PREPARING / RECORDING / PAUSED / FINALIZING / SAVED / FAILED, só declara salvo após `VideoRecordEvent.Finalize` sem erro e URI válida.
- Microfone **desativado por padrão**, solicita `RECORD_AUDIO` só quando usuário o ativa. Sem permissão, grava silêncio; nenhum upload ou servidor.
- UI Jetpack Compose existente preservada, com disparador REC/STOP, cronômetro, PAUSE/RESUME, ícone de microfone, barra de Foto/Vídeo e bloqueios durante gravação.
- Botões de troca de câmera, mudança de modo e transição noturna são bloqueados enquanto grava/finaliza para impedir rebind. No background, vídeo é finalizado.
- Galeria local consulta fotos em Pictures e vídeos em Movies, ordena por data e oferece reprodução por aplicativo Android compatível.
- Seletor de filmes permanece visível para foto. **Importante:** vídeos nesta etapa são MP4s sem filtros; não existe efeito falso no preview nem no arquivo. A fase P3 implementará `CameraEffect` para o mesmo look no preview e no vídeo.

## Não entregue nesta etapa
- Efeitos analógicos no vídeo (16 mm, Super 8, VHS, MiniDV, CCD).
- Shader GPU e exportação offline Media3.
- Seleção de FPS e proporções personalizadas ou HDR.
- Gravação em background / transição entre câmeras durante REC.
- Player de MP4 incorporado na galeria (usa app Android externo via ACTION_VIEW).
- Equivalência de efeitos entre foto e vídeo; isso exige implementação específica GPU.

## Verificações obrigatórias
1. Abre em Foto, tira JPEG, aparece na galeria.
2. Alterna Vídeo, preview não desaparece, botão vermelho de gravação aparece.
3. Ativa microfone: permissão solicitada uma vez; sem conceder, vídeo silencioso.
4. Grava 10 segundos, pausa, retoma, para; relógio e estados refletem realidade. MP4 visível na galeria e reproduzível, áudio sincronizado quando permitido.
5. Durante REC, Foto/Noite/troca de câmera não desmontam a sessão.
6. Sair e reentrar em Vídeo/Foto várias vezes sem crash/black preview.
7. App para no background com finalização; arquivo original preservado quando possível.
8. Verificar câmera frontal/traseira e dispositivos sem suporte ao controle manual.
9. Após erro de encoder, interface retorna estado de falha e permite nova tentativa.
10. Executar `app:testDebugUnitTest`, `app:connectedDebugAndroidTest`, gravar vídeo em **Android físico** e inspecionar MediaStore.

## Limitações e riscos remanescentes
- Sessões CameraX reinicializam ao **trocar Foto/Vídeo**, não ao abrir painéis; uma transição curta no visor pode ocorrer.
- O efeito de filme aplicado ao preview fotográfico não pode ser considerado persistido em MP4 até implementar a etapa GPU.
- Alguns OEMs não aceitam gravação com extensões noturnas: vídeo evita Night extension.
- Sem emulador validado ou teste no aparelho real, a simples compilação não comprova gravação nem áudio; o PR não deve ser incorporado à main.

## Documentos relacionados
- `docs/VIDEO_RETRO_HORROR_SPEC_V1.md` na branch de documentação de vídeo (PR #13).
- Plano P1-P7; após estabilizar P1, começar catálogo e cadeia GPU compartilhada.
