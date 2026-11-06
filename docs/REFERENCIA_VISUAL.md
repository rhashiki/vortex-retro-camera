# Referência visual — fotografia analógica de rua

## Referência fornecida pelo proprietário (06/11/2026)
Vídeo enviado na conversa: câmera preta com acabamento texturizado, contador de exposições, visor em orientação vertical, seletor separado de câmera, lente 35 mm e película, disparador circular e apresentação da imagem como foto em papel.

A intenção é adotar a **linguagem visual**, não copiar marca, layout pixel a pixel, imagens ou os nomes próprios de perfis de outro aplicativo. Identidade do produto: Vortex Retro Camera.

## Qualidade fotográfica desejada
- Fotografia de rua ensolarada: predominância de tons quentes de amarelo/âmbar, com vermelho vivo, verdes agradáveis e azuis/cianos suavizados.
- Altas luzes luminosas e de transição suave; preservar textura quando houver informação no original, em vez de recortar brancos artificialmente.
- Sombras com cor (leve verde/ciano), curva tonal com pretos discretamente erguidos, contraste moderado e aparência de impressão.
- Granulação dependente de luminância e perfil de filme, sem ruído RGB uniforme por pixel.
- Vinheta e aberração cromática discretas; evitar exageros.
- Carimbo de data âmbar opcional baseado na data real; não falsificar metadados.
- Half Frame: recorte vertical 3:4 e guia correspondente na câmera; combinação de duas exposições ainda pendente.
- Após disparo, apresentar foto como revelação sobre fundo papel (ainda pendente).

## Comportamento de interface
1. Câmera com corpo preto/grafite sutilmente texturizado; visor central; grande disparador redondo.
2. Composição da receita: câmera / lente / filme devem ser seleções independentes em versão futura.
3. Contador e tipografia inspirados em equipamentos físicos; controles de Pro, Panorama e Vídeo continuam acessíveis.
4. Preview e foto final devem convergir no futuro por pipeline de GPU; não chamar a tonalização indicativa atual de WYSIWYG.
5. Film Engine compartilhado para Foto, Pro, Panorama e Vídeo após esses módulos existirem.

## Entrega incremental
- [x] Base Kotlin CameraX (M1)
- [x] Half Frame simples (recorte 3:4) e carimbo de data opcional (esta alteração)
- [ ] Enquadramento e processamento idênticos entre preview e JPEG final
- [ ] Curvas tonais calibradas, bloom/halation de alta qualidade e grão dependente de luminância
- [ ] Interface de câmera vintage refinada, seletor de câmera/lente/filme
- [ ] Animação de revelação + álbum
- [ ] Diptych verdadeiro de duas exposições Half Frame
- [ ] Testes fotográficos em diferentes cenas e aparelhos

**Limitação atual:** o filtro salvo ainda é um tratamento CPU simples. Esta referência descreve uma meta visual, não fidelidade validada à emulsão de qualquer fabricante.
