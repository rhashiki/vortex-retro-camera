# M3 — Iluminação, flash e Night Capture

## Funções adicionadas
- Laboratório de luz: Brilho, Contraste, Sombras, Realces, Saturação e Temperatura, de -100 a +100, com botão para restaurar. São controles digitais que alteram o JPEG exportado; não fingem atuar fisicamente no sensor.
- Prévia: aproximação em tempo real de Brilho, Contraste, Saturação e Temperatura através de `ColorMatrixColorFilter`. Sombras/Realces e granulação são processados na fotografia final; isto não é WYSIWYG completo.
- Flash físico: OFF, AUTO, ON usando `ImageCapture.flashMode`; detecção por `CameraInfo.hasFlashUnit()`, sem prometer flash frontal em aparelhos sem LED.
- Modo Noite: consulta o suporte `ExtensionsManager` / `ExtensionMode.NIGHT` para câmera escolhida. Se o fabricante fornecer essa extensão, o CameraX a utiliza para captura real, inclusive processamento multi-frame fornecido pelo fabricante.
- Fallback sem extensão: adquire TRÊS fotografias consecutivas (não simula apenas clareamento), alinha deslocamentos pequenos por busca luminosa e faz redução de ruído com rejeição simples de movimento. Exporta resultado com as mesmas receitas da película.
- Em Noite: flash OFF; não há captura de vídeo noturno. Pro manual fica desativado em Night OEM para evitar conflito de controles.
- Pequeno ajuste de compensação real de exposição, quando disponível, no caminho de fallback.
- Fotos permanecem locais no armazenamento do telefone.

## Limites honestos
- Fusão de três quadros tem menos recursos que os algoritmos comerciais; movimento, pouca luz extrema ou cena muito escura podem gerar resultados inferiores.
- Para obter resultados melhores, recomenda-se apoiar o celular ou manter firme até completar a captura.
- RAW e captura com tempo arbitrariamente longo ainda precisam de implementação.
- O modo Noite OEM depende do hardware/implementação do fabricante e não está disponível em todos os aparelhos.
- Foco manual, telefoto/flash dependem do dispositivo.
- Integração Foto/Pro/Panorama/Vídeo do motor GPU ainda está pendente; Panorama/Vídeo não foram implementados.
- Versão de desenvolvimento; testes em aparelho físico são necessários.

## Matriz de testes solicitada
1. Fotografar com `OFF`, `AUTO`, `ON` em local pouco iluminado, confirmando que o LED dispara conforme o caso.
2. Ativar Noite: verificar mensagem `PROCESSAMENTO DO FABRICANTE` ou `3 QUADROS`, fotografar objeto estático e comparar ruído com Foto normal.
3. Verificar que Noite sempre desliga flash e não conflita com foco/manual Pro.
4. Ajustar as seis propriedades, conferir alterações na imagem salva e no visor quando possível.
5. Trocar câmera frontal/traseira e checar avisos de recursos ausentes.
6. Tirar fotos sucessivas e verificar galeria sem travamentos.
7. Comparar cena com movimento: rejeição imperfeita de ghosting é uma limitação conhecida.
