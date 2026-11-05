# Vortex Retro Camera — roadmap

## M1 · Fundação (este pacote)
- [x] Projeto Android nativo Kotlin, offline, CameraX 1.6.2.
- [x] Tela da câmera, alternância traseira/frontal, temporizador de disparo simples.
- [x] Captura JPEG, rotação EXIF e salvamento no MediaStore.
- [x] Seis perfis iniciais de filme/câmera processados localmente em CPU.
- [x] Modo Pro experimental: ISO e tempo de exposição em hardware MANUAL_SENSOR.
- [x] Workflow CI para gerar APK de debug por push/PR.
- [ ] Build/validação em Android real e compatibilidade em vários sensores.

## M2 · Motor visual e qualidade
- [ ] Preview exata via GPU compartilhando LUT com exportação.
- [ ] Perfil calibrado: curvas por faixa tonal, halation, bloom, cromia, luz e grão não uniforme.
- [ ] Pipeline 12MP/48MP com processamento tiled sem estourar RAM.
- [ ] Fotos RAW/DNG e metadados EXIF completos onde suportados.
- [ ] Testes golden image e comparação com referências escaneadas legalmente obtidas.

## M3 · Pro avançado
- [ ] WB Kelvin/tint, foco manual, AE lock, zebra, histograma, peaking.
- [ ] Lens selection, EV, grade, horizonte, shutter controls e capabilities fallback.
- [ ] Separar ajustes físicos (sensor) de simulações de filme.

## M4 · Panorama real
- [ ] Guidance com giroscópio, captura sequencial e controle de exposição.
- [ ] Correspondência de features, correção de perspectiva, blend multiband.
- [ ] Exportação panorâmica com os mesmos perfis de filme.

## M5 · Vídeo retrô real
- [ ] Pipeline MediaCodec + GPU para gravar frames efetivamente filtrados.
- [ ] Perfis MiniDV, Hi8, VHS-C, Sony Cyber-shot CCD e cine 8/16 mm.
- [ ] Áudio, FPS, estabilização e sincronização A/V.
- [ ] Exportar MP4/H.264 ou HEVC conforme encoder suportado.

## M6 · Polimento e APK
- [ ] Permissões e lifecycle, rotação, background/foreground, thermal throttling.
- [ ] Cobertura de testes, benchmark, crash monitoring local opcional.
- [ ] APK assinado release por pipeline seguro; debug test build via Actions.
- [ ] Teste em pelo menos 3 dispositivos Android diferentes.

**Importante**: itens marcados como pendentes não estão funcionando. M1 é base inicial, não versão concluída.
