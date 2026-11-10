# Vortex Retro Camera — painel de filtros por miniaturas (v0.5.2-alpha)

## Referência
Imagem de UI Premium enviada em 10/11/2026: faixa horizontal com miniaturas de paisagem, rótulos como Vintage 200 / Kodak Gold / Fuji, contorno roxo no filtro ativo. O usuário pediu explicitamente que **as miniaturas apareçam só quando abrir o painel de filtros**.

## Entrega
- Novo componente Android nativo `FilmFilterDrawer`, inicializado com `View.GONE`; não consome altura da interface enquanto fechado.
- Ao tocar em **Filtros** ou no botão da película selecionada, alterna entre expandido/fechado.
- Painel aberto contém cabeçalho `FILTROS & EFEITOS`, botão `FECHAR` e faixa horizontal rolável de cartões retangulares de 98×104 dp, nomes e bordas arredondadas.
- Filtro selecionado destacado em roxo com borda mais espessa; ao tocar no cartão, altera `selectedPreset` e atualiza a tonalidade da prévia do visor. O painel permanece aberto para comparação.
- Cada miniatura é gerada **offline** usando um pequeno recorte da imagem atual do `PreviewView` e o mesmo `FilmProcessor` empregado no JPEG exportado (com sorteio de queimas desligado apenas para a miniatura). A geração acontece numa fila separada para não bloquear a captura.
- Enquanto a câmera não tem um quadro disponível, exibe cartões em gradiente neutro e o texto `Aguardando imagem da câmera`. Não carrega fotos genéricas ou imagens de rede.
- A prévia é aproximada: o próprio PreviewView pode já exibir tonalidade do filme ativo. O motor fotográfico exportado permanece a fonte de verdade.
- A geração é cancelada logicamente quando se fecha o painel; resultados obsoletos não são publicados.
- Layout paisagem mantém o painel dentro da área rolável da barra lateral; disparador e modos permanecem fixos.

## Compatibilidade
Sem PWA, rede, SDK externo, licença paga, login ou permissões novas. `main` não é alterada.

## QA
- Abrir app pela primeira vez: nenhuma miniatura de filtro visível.
- Abrir Filtros pelo botão rápido e pelo botão da película: cartões aparecem; FECHAR e segundo toque escondem.
- Escolher Kodak Gold / Fuji Superia / Sony CCD: muda o filme do visor e destaca o cartão.
- Com câmera sem permissão: painel não trava; avisa que espera a imagem.
- Em telas pequenas, girar para paisagem e testar se o disparador continua acessível.
- Rodar teste instrumentado `filtersStayHiddenUntilExplicitlyOpened` e teste de inicialização em emulador Android.
