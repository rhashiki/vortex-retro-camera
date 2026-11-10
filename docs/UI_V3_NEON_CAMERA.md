# Vortex Retro Camera — UI Premium v3 (referência visual do usuário)

## Objetivo
Aplicar a linguagem do screenshot enviado em 10/11/2026: preto profundo, roxo neon, vidro escurecido, grande visor, grade de composição, focagem no centro, controle lateral de zoom, controles de captura na base e modos na barra inferior. A captura permanece **Android nativa CameraX**, sem PWA, navegador ou servidor.

## Decisões
- Nome do produto permanece **Vortex Retro Camera**. A interface pode exibir "Vortex Camera" para evitar ocupação excessiva do cabeçalho.
- A fotografia do pôr do sol da referência **não** está embutida no aplicativo; o visor mostra a câmera real.
- Barra superior: flash físico, HDR (em desenvolvimento), AI (em desenvolvimento), ajustes. Botões em desenvolvimento **não** simulam processamento; explicam sua condição.
- Informações sobre foto: contador de exposições real salvo no SharedPreferences, formato 4:3 e exportação JPEG.
- Histograma não decorativo: dados Y (luminância) de quadros ImageAnalysis quando o hardware aceita binding Preview + ImageCapture + ImageAnalysis. Identificado como **LUMA** — não promete canais RGB. Quando não suporta, sinaliza indisponível.
- Zoom: mostra **apenas** fatores dentro de minZoomRatio e maxZoomRatio disponíveis na lente. Não promete fisicamente 0,5×, 2×, 5× ou ultra-wide se o aparelho não suportar.
- Visor: grade 3×3, foco reticular, leve vinheta, marcador de foco real por toque.
- Linha inferior de edição: Ajustes (laboratório de luz), Filtros e película (seletor), Efeitos/Grão (textura da emulsão).
- Disparador branco com aro roxo, botão de galeria e alternância frontal/traseira.
- Navegação inferior: Retrô, Vídeo, Foto, Pro, Noite, Pano. Os modos de foto, Pro e Noite reutilizam a implementação existente; Vídeo e Panorama **permanecem em desenvolvimento**.
- Escolher Retro abre o catálogo de películas, sem ocultar recursos.
- Retrato: câmera ocupa todo o fundo; cabeçalho e dock fixos separados dos painéis.
- Paisagem: trilho lateral com as mesmas ações em área rolável. Preview continua ao fundo.
- Todos os controles e processamento permanecem locais; IA de melhoria e remoção de objetos **não** está integrada ainda, depende da seleção/licença dos modelos, benchmark e QA.

## Casos de teste visual
- 320×640, 360×720, 393×852 dp, fonte normal / ampliada, system bars, cutout e teclado.
- Paisagem 740×360 dp: câmera não pode desaparecer sob painel; ajustes em área rolável.
- Pro abre AE automática, não força 1/125s e ISO 400.
- Noite liga/desliga e mantém estado visual roxo.
- Galeria da última captura e novo acesso: conteúdo cortado somente dentro da borda.
- Flash indica N/A em câmeras sem flash.
- Seletor exibe todos os perfis e atualiza o chip atual.
- Zoom oferece apenas valores permitidos, e histograma reflete a imagem.
- HDR e IA explicam que estão indisponíveis, sem efeito placebo.

## Critério de entrega
O GitHub Actions confirma compilação e testes unitários; **responsividade premium depende de QA visual em dispositivos reais ou emulador**. Não confundir build aprovado com validação de UX.
