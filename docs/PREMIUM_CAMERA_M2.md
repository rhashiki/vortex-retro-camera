# Vortex Retro Camera — M2 Premium / feedback 06-11-2026

## Origem
Gravação de tela do app de testes enviada pelo proprietário em 06/11/2026.
Problemas identificados: miniatura de galeria extrapolava moldura; barra de status Android sobrepunha cabeçalho; composição excessivamente ocupada com controles; ausência de AF/MF e efeitos analógicos fortes.

## Entregue na branch feat/premium-focus-film-effects
- **Autofoco por toque**: CameraX `FocusMeteringAction`, com ponto visível e resultado da focalização.
- **Foco manual óptico**: Camera2 `LENS_FOCUS_DISTANCE`, disponível apenas quando `LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0`; valor normalizado de infinito à distância mínima.
- **Integração Pro**: uma única solicitação de interop agrega ISO/obturador + distância focal, para não se sobrescreverem.
- **Miniatura da galeria**: recorte com `clipToOutline`, `clipChildren` e margens internas, `CENTER_CROP`.
- **UI**: content insets para status/nav do Android 15+, toolbar compacta e ajustes em painéis inferiores.
- **Película**: intensidade do grão ajustável 0 a 300%, com distribuição de ruído dependente da luminosidade; vazamento de luz âmbar em bordas aleatoriamente entre 0 e 50% das capturas (padrão 16%), não aplicado em simulação Sony CCD.
- **Som**: som original sintetizado no aparelho, com duplo estalo mecânico e breve avanço de filme; pode ser desativado em Ajustes. Não é gravação fiel de um modelo específico e não substitui som de obturador obrigatório do fabricante.
- **Sem backend**: funcionamento offline, sem conta e sem upload.

## Itens ainda a validar
- UX nas resoluções e recortes variados, toque e captura em sensores com AF e MF, áudio nos modos som/silencioso e compatibilidade do MediaStore.
- Comparação fotográfica entre filtros e referências em cenas claras/escuras; efeitos não são perfis calibrados de fabricantes.
- Preview WYSIWYG dos efeitos de grão/vazamento exige renderer GPU unificado. Atualmente preview é aproximação colorimétrica.
- Panorama e Vídeo permanecem não implementados.

## Testes do alpha
1. Instalar debug, permitir câmera, tirar foto e tocar na miniatura da galeria.
2. Observar se imagem respeita borda arredondada do botão.
3. Na câmera, tocar em diferentes profundidades (perto/longe) e procurar o marcador de foco.
4. Em FOCO, escolher MF e mover slider, verificando disponibilidade real do sensor.
5. Em TEXTURA, escolher grão e probabilidade de queima.
6. Em AJUSTES, ligar/desligar o disparo mecânico e confirmar data/temporizador.
7. Verificar que fotos não desaparecem quando volta para a câmera.
