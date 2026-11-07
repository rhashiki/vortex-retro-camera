# Vortex Retro Camera — UI v2 Premium

## Diagnóstico

O design anterior compunha a tela em uma única coluna `LinearLayout`: cabeçalho, visor, filmes, opções, modos, botões e rodapé. Em celulares com menor altura útil e com barras do Android, o controle Noite se tornava inacessível ou cortado.

## Nova arquitetura

Reconstruída do zero em um `FrameLayout` de câmera com visor de borda a borda e painéis flutuantes. Os controles importantes não participam da rolagem dos painéis de filmes e ajustes.

**Retrato:** cabeçalho flutuante fixo com identificador da marca e mostrador de exposições; linha fixa de Noite / Flash / Foco / Luz; painel inferior flutuante para seleção horizontal de filmes, alternância de modos e disparo; acesso à galeria em miniatura recortada.

**Paisagem:** visor principal à esquerda e barra lateral direita (236 dp) com cabeçalho e Noite / Flash / Foco / Luz em grade 2x2 fixa no topo; zona central rolável de filmes/modos; disparador e miniatura sempre fixos na base. Nenhuma barra vertical empilhada sobre o visor.

**Em qualquer orientação:** safe areas via `WindowInsetsCompat.Type.systemBars()`; controles de 40–49 dp, botão de disparo 68 dp com aro 83 dp; texto de alto contraste; estados de Noite, flash e modo ativos visualmente diferenciados.

## Navegação

- **Noite** permanece **sempre visível** na linha de controles superior (ou na barra lateral de paisagem). Estado ativado usa fundo dourado e texto escuro; durante o disparo a interface indica extensão OEM ou 3 quadros.
- **Flash:** OFF/AUTO/ON (quando disponível), sem funcionamento no modo Noite.
- **Foco:** abre AF/MF com slider somente em hardware suportado; o visor permanece clicável para AF.
- **Luz:** Brilho, Contraste, Sombras, Realces, Saturação e Temperatura, com controles roláveis.
- **Configurações:** engrenagem no cabeçalho, incluindo textura, Half Frame, data, temporizador e som.
- **Filmes:** carousel horizontal, item selecionado centralizado e destacado.
- **Modos:** Foto / Pano / Pro / Vídeo; Pro abre painel dedicado, sem reduzir visor.
- **Galeria:** miniatura dentro de moldura de 62 dp, `CENTER_CROP` e clipping arredondado.

## Estados e layout

- Parâmetros de filme, modo, noite/flash, ajustes tonais, half-frame e foco são restaurados na rotação do dispositivo.
- Nenhuma alteração no motor Night, Gallery, CameraX ou processamento de filmes nesta revisão, exceto integração de estado com a nova interface.
- Panorama e Vídeo continuam não implementados. Não ocultar essa limitação.

## QA necessário em aparelho

| Orientação | Cenário | Critério |
|---|---|---|
| Retrato pequeno (360×640 dp) | Barra do Android / fonte padrão | Noite e Flash à vista, disparador acessível |
| Retrato alto (390×840 dp) | Trocar filme e abrir Luz | Visor ocupa centro; painel rolável |
| Paisagem (740×360 dp) | Noite + Flash | Ambos fixos no canto superior da barra lateral |
| Qualquer | Aumentar tamanho da fonte | Textos sem atravessar controles; ações identificáveis |
| Qualquer | Rotacionar enquanto Noite ativo | Noite preservado; captura reinicializa de forma segura |
| Qualquer | Tocar foto e miniatura | Sem overflow; galeria abre |
| Qualquer | Foco/Pro | Painéis não cobrem permanentemente o visor |

A compilação verifica o código, mas **não valida a responsividade real por screenshot**. Essa parte depende de testes no Android.
