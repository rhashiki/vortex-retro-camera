# Vortex Retro Camera — Modo Vídeo Retrô / Horror
Versão da especificação: 1.0 | Status: PROPOSTA TÉCNICA, NÃO IMPLEMENTADO
Alvo: Kotlin + Jetpack Compose + CameraX 1.6.2/Camera2, Android minSdk 29, totalmente offline.

## 1. Objetivo
Vídeos com aparência de película cinematográfica, filme caseiro e filmadoras dos anos 2000: resposta de cor, foco, textura, falhas temporais e áudio opcional. Diferente de um filtro de tela: os efeitos PRECISAM aparecer no MP4 salvo e ser consistentes com o visor.

Referência estética principal: os filmes encontrados em A Entidade 2 (Sinister 2, 2015) foram gravados principalmente em película **16 mm**, depois degradados com riscos e vazamentos de luz. O primeiro filme utilizou Super 8. Confirmado pelo diretor Ciarán Foy em https://www.fangoria.com/ciaran-foy-interview-sinister2/ . A simulação Vortex será original; não copiar cenas, trilhas, texturas ou outros assets do filme.

## 2. Estado real do projeto (branch feat/compose-reference-exact-ui)
- app/build.gradle.kts: CameraX 1.6.2, Compose, minSdk 29, targetSdk 35; NÃO contém camera-video.
- MainActivity.kt: PreviewView, ImageCapture, ImageAnalysis, MediaStore de fotos, integração com UI Compose. Vídeo atualmente é uma opção sem motor de gravação.
- camera/ManualCamera.kt: foco manual por Camera2 e ISO/obturador físico apenas com capacidade MANUAL_SENSOR; reutilizar.
- film/FilmPreset.kt: dez receitas de coloração de foto (incluindo Kodak/Fuji/CCD), todas aproximações autorais.
- film/FilmProcessor.kt: loop CPU de pixel por bitmap para foto JPEG; NÃO executar por quadro em vídeo.
- camera/NightFrameFusion.kt: função de foto noturna, deve permanecer independente da sessão de vídeo.
- ui/CameraScreen.kt: interface Premium aprovada; manter visor PreviewView por AndroidView, preservar visual, substituir somente os estados/controles de Vídeo.
- Manifest: CAMERA, sem RECORD_AUDIO, sem INTERNET. Solicitar microfone opcional com permissão em runtime quando áudio for implementado.

Não criar servidor, não alterar backend inexistente, não apagar motor de foto, galeria ou filtro.

## 3. Catálogo de perfis V1
Escala 0-1 é artística e deve ser ajustada com amostras, nunca apresentada como calibração física.

| Perfil | Base | Proporção | Cadência / FPS | Grão | Flicker | Jitter | Riscos | Vazamentos | Resultado desejado |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| **16 mm Found Footage** (principal) | 16 mm negativo | 4:3 | 24 fps | .68 | .13 | .20 | .28 | .12 | sombras densas, cores frias contidas, textura/halation |
| **Super 8 Horror** | Super 8 | 4:3 | aparência 18 fps | .84 | .25 | .32 | .31 | .18 | grão grande, cadência irregular, aspecto caseiro sombrio |
| **8 mm Home Movie** | 8 mm caseiro | 4:3 | aparência 18 fps | .60 | .10 | .21 | .14 | .20 | nostalgia quente, desbotamento suave |
| **MiniDV 2000** | fita digital | 4:3 | 25/30 fps | .08 | .04 | .01 | 0 | 0 | oversharpen, CCD noise, WB imperfeito, interlace opcional |
| **VHS-C Dirty** | fita analógica | 4:3 | 25/30 fps | .13 | .14 | .10 | 0 | 0 | chroma bleed, tracking raro e dropouts discretos |
| **CCD Cybercam 2000** | compacta digital | 4:3 | 15/25/30 conforme suporte | .10 | .06 | 0 | 0 | 0 | highlights duros, baixa faixa dinâmica, compressão |

Cadência a 18 fps é SOMENTE VISUAL quando o encoder/sensor não permitir esse FPS: repetir/segurar quadros determinísticos num arquivo 24/30 fps, mantendo timestamps e sincronização de áudio corretos. Não forçar obturador físico sem suporte.

## 4. Motor visual, passes GPU
Ordem padrão para SDR: (1) textura da câmera/transformações, (2) curva de cor/LUT e gama, (3) vinheta/halation/bloom de altas luzes, (4) granulação dependente da luminância, (5) scratches/poeira/queima temporal, (6) flicker/gate weave, (7) artefatos específicos de VHS/MiniDV/CCD, (8) crop/apresentação 4:3/data stamp opcional.

- Grão procedural varia por frame, cresce nas sombras, seed/frameIndex determinísticos.
- Flicker é uma oscilação sutil e suave, nunca flashing forte.
- Jitter/gate weave é deslocamento subpixel, limitado para não cortar excessivamente o vídeo.
- Riscos persistem alguns frames com local coerente, eventos raros e não periódico.
- Queimas/light leaks são eventos esporádicos e transicionais, não manchas constantes.
- Halation só reage a altas luzes; usar downsample parcial e buffers GPU reutilizáveis.
- VHS: desalinhamento de crominância, tracking horizontal e dropouts pontuais.
- MiniDV: halo de nitidez, luma/chroma limitada, ruído eletrônico e interlace opcional.
- Foco: AF e MF reais por Camera2 quando suportados; hunting artificial só com escolha explícita. ISO/exposição física capability-gated. Ajustes criativos não são apresentados como ajustes físicos de sensor.
- O som de mecanismo, hiss e vento de projetor é camada gerada/autoral opcional, não substitui o áudio do microfone e não usa áudio do filme.

**Proibição técnica:** efeitos desenhados sobre Compose/PreviewView NÃO são gravados automaticamente. O MP4 deve receber o mesmo shader ou ser reprocessado offline. Também não usar FilmProcessor de bitmap por quadro 1080p.

## 5. Arquitetura incremental
Adicionar pacote video/ sem refatorar desnecessariamente os módulos de foto:

- VideoRecordingController.kt — gravação VideoCapture<Recorder>, status e MediaStore.
- VideoSessionBinder.kt — binding seguro Preview + VideoCapture; fallback quando use cases incompatíveis.
- VideoCapabilityResolver.kt — QualitySelector, VideoCapabilities, FPS/encoders, mic, controles físicos.
- RetroVideoProfile.kt — catálogo e presets persistíveis, seed, parâmetros de shader.
- RetroVideoEffect.kt — CameraEffect com PREVIEW | VIDEO_CAPTURE.
- GpuFilmSurfaceProcessor.kt — SurfaceProcessor e OpenGL ES na GPU, lifecycle dos Surface.
- VideoLookRenderer.kt — passes de shader reutilizados para Preview e output do encoder.
- VideoExportWorker.kt — fallback de renderização offline Media3 Transformer, arquivo original preservado.
- VideoAudioEngine.kt — áudio original/microfone e efeito opcional, exclusivamente local.
- VideoDiagnostics.kt — dropped frames, thermal, encoder error, memória e sincronização A/V.
- ui/video/VideoModeControls.kt — Composables de REC/STOP/PAUSE/tempo.
- ui/video/VideoProfileCarousel.kt — seleção de 6 perfis, oculto por padrão.
- ui/video/VideoManualPanel.kt — FPS, qualidade, WB, ISO, shutter, foco/EV reais quando disponíveis.

### 5.1 Gravação real
Acrescentar dependência androidx.camera:camera-video:1.6.2 e manter mesma versão de todas as libs CameraX. Construir Recorder, usar QualitySelector filtrado pelas capacidades reais, bindar VideoCapture com Preview e chamar PendingRecording.start() / Recording.pause,resume,stop(). Persistir através de MediaStoreOutputOptions após evento Finalize; antes disso mostrar salvando e nunca afirmar sucesso.

Estados exatos: Idle -> Preparing -> Recording <-> Paused -> Finalizing -> Saved | Failed. Bloquear dupla gravação, troca de câmera e rebinding durante Recording. Tratar background/foreground, câmera indisponível, gravação abortada, falta de espaço, erro de encoder, rotação, permissão negada. Não pedir RECORD_AUDIO quando gravação silenciosa. Não manter WAKE_LOCK por padrão sem necessidade.

### 5.2 Preview = vídeo gravado
CAMINHO PRINCIPAL: CameraEffect + SurfaceProcessor com targets PREVIEW | VIDEO_CAPTURE, suportados pelo CameraX; mesmo render OpenGL em ambas as superfícies. Configurar UseCaseGroup, crop, orientação e SurfaceRequest/SurfaceOutput com transformação adequada. Preview e MP4 precisam mostrar os mesmos parâmetros de filme; brilho/grão temporal determinístico por timestamps. Se a combinação de use cases não for suportada, reduzir exigências (como ImageAnalysis), nunca quebrar gravação para manter histograma.

FALLBACK: gravar arquivo limpo com Recorder, processar cópia filtrada offline pelo Media3 Transformer com efeitos próprios e apresentar progresso local ao usuário. Conservar o MP4 original em caso de falha. Nunca mostrar filtro só no visor e salvar arquivo limpo sem informar.

Fontes: https://developer.android.com/media/camera/camerax/video-capture ;
https://developer.android.com/reference/androidx/camera/core/CameraEffect ;
https://developer.android.com/reference/androidx/camera/core/SurfaceProcessor ;
https://developer.android.com/media/media3/transformer/transformations .

### 5.3 Interface Compose
Tela original aprovada: preto profundo/roxo Vortex, preview vertical imersivo, zoom físico, grade e histograma opcional, barra superior, barra flutuante e navegação inferior. Ao ativar Vídeo, disparador assume aro vermelho discreto, cronômetro 00:00, botão alterna Iniciar/Parar, Pausar quando possível, sinal inequívoco de gravando, mic ligado/desligado, galeria após Finalize.

Perfil de filme exibido como chip; carrossel com miniaturas abre APENAS quando o usuário tocar. Controles avançados de gravação e efeitos se abrem em painéis recolhíveis sem desligar preview e sem rebinding. Ao iniciar gravação, travar perfil/qualidade e ações que interrompam sessão. Na primeira versão, alterar perfil durante a gravação será desabilitado para evitar resultados incoerentes.

Modo Pro começa com AE automática; habilitar controles manuais só por escolha explícita. Quando hardware não suportar ISO/foco/etc., controles ficam desabilitados com motivo. Vídeo 16mm/8mm/Horror são presets de estética, não promessas de formato físico real.

## 6. Recursos por classe de dispositivo
| Hardware, definido por capabilities + benchmark | Alvo inicial | Efeitos |
| --- | --- | --- |
| Básico | 480p 24/30 fps se suportado | coloração/vinheta/grão leve |
| Médio | 720p até 30 fps | coloração, grão, halation simples, flicker |
| Alto | 1080p 24/30 fps ou mais somente se testado | todos os passes do perfil |
| Especial | 4K ou 60 fps | só habilitar se encoder, GPU e controle térmico aprovados |

Requisitos de desempenho: nenhuma cópia Bitmap por frame no main thread; FBO/GL reutilizados; limitações térmicas via PowerManager quando disponíveis; prioridade à gravação e sincronização A/V sobre efeitos caros. Ao degradar qualidade, informar. Metas (não promessas) em aparelho real: gravação contínua de 5 min a 720p/30, sem OOM, sem vídeo corrompido, áudio sincronizado, nenhuma abertura de painel interrompendo a sessão.

## 7. Roadmap e critério de aceite
**P0 | Congelamento do estado atual:** branch separada, testes de regressão Foto/Noite/Pro/galeria, capacidades de câmera. Sem merge main.
**P1 | Vídeo de verdade sem efeitos:** CameraX VideoCapture, permissões, salvar MP4, capturar som opcional, REC/STOP/PAUSE e estados em Compose. Primeiro MP4 reproduzível.
**P2 | UI e perfis:** cartões dos seis estilos, preview de seleção, controles ocultos por padrão, capability gating.
**P3 | GPU compartilhada:** colorização, vinheta e grão efetivos no preview + gravação. Verificar quadros do MP4.
**P4 | Filme 16mm:** halation, flicker, jitter, scratches, burn/leaks e seed determinística. Perfil principal inspirado na estética técnica de A Entidade 2.
**P5 | Demais perfis:** Super 8/8mm/MiniDV/VHS/CCD com artefatos próprios e refinamento artístico.
**P6 | Áudio + fallback:** ruído mecânico opcional, exportação local Media3, processamento seguro e reversível.
**P7 | Homologação:** screenshots do Compose, gravação em 3 celulares diferentes, logs de performance, temperatura, bateria, codecs, falhas de armazenamento, lifecycle e sincronização de áudio.

Checklist de aceite:
- [ ] Um MP4 com vídeo real pode ser gravado e reproduzido na galeria
- [ ] O som é opcional e funciona apenas após consentimento
- [ ] Nenhum painel abre sozinho ou bloqueia permanentemente o shutter
- [ ] O mesmo look aparece realmente no MP4 e no visor (ou fallback explícito em pós)
- [ ] Perfil 16mm e mais cinco perfis exibem diferenças mensuráveis de imagem/cadência
- [ ] Foto, Noite, Pro, filtros e galeria antigos seguem funcionando
- [ ] Sem crash de startup/rotação/pausa/limite de armazenamento
- [ ] Performance e aquecimento medidos em dispositivo físico
- [ ] Build + testes instrumentados aprovados; APK de testes gerado
- [ ] Nada é marcado como concluído apenas por compilação

**Entrega funcional mínima = P1.** Antes de um Recorder funcionando, o botão Vídeo não deve simular gravação. **Conclusão final = vídeo com look original reproduzível, offline, sem regressões.**

## 8. Privacidade, autoria e limites
Nenhum download de modelos, backend, internet, conta, telemetria ou trilha licenciada. Som de projetor e texturas precisam ser originais ou licenciados, não retirados do filme. Perfis são simulações autorais e não calibrações oficiais Kodak/Fuji/Sony. A IA offline de restauração é outra frente, opcional, sem bloquear a entrega do vídeo retrô.
