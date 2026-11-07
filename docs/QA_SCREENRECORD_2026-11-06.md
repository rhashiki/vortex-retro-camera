# Revisão de gravação de tela — Vortex Retro Camera v0.4.0

Origem: gravação de 70,7 s do usuário, enviada na conversa em 06/11/2026, aproximadamente às 21h26 (horário do aparelho).

## Observações visíveis
- Ações Noite, Flash, Foco e Luz estão presentes na barra superior na orientação retrato.
- O usuário percorre flash, noite, foco AF/MF, controles ISO/obturador Pro e seleciona películas.
- Ao entrar em Pro (perto de 28 s), o preview fica muito escuro. Na versão anterior, `setMode(PRO)` chamava `applyManual()` com ISO 400 e 1/125 s sem ação do usuário; a exposição automática era imediatamente desligada, explicando a redução drástica de luminosidade na cena interna.
- Com MF ativo, toques no visor produzem avisos temporários repetidos ("Foco manual ativo. Ajusta pelo botão FOCO"). Isso atrapalha compor uma fotografia.
- Painéis de MF/Pro são grandes em retrato.

## Correções desta branch
1. **Pro inicia com AE automático**: somente ao tocar em "ATIVAR ISO / OBTURADOR MANUAIS" começa a enviar `CONTROL_AE_MODE_OFF` e os parâmetros de ISO/tempo à Camera2.
2. Botão para **voltar ao AE AUTO**; ao sair de Pro o controle manual de exposição é liberado, preservando MF quando desejado.
3. Sensores sem suporte exibem claramente AE automático e não tentam aplicar controles manuais.
4. Os ajustes Pro são reavaliados após a câmera terminar de inicializar; os parâmetros manuais são preservados na rotação quando já escolhidos explicitamente.
5. Em MF, toques no visor não disparam toast repetitivo. O ajuste continua pelo painel de foco.
6. Painéis inferiores de ajustes têm altura máxima menor no retrato e são roláveis.

## Teste de regressão em Android real
- Comece em Foto, com uma cena interna; toque PRO. A exposição deve **permanecer parecida**; rótulo "AE • EXPOSIÇÃO AUTOMÁTICA".
- No painel Pro, selecione "ATIVAR ISO / OBTURADOR MANUAIS"; o preview pode mudar de brilho de acordo com os valores físicos — isso é intencional.
- Use "VOLTAR AO AUTO (AE)"; a cena deve recuperar a exposição automática.
- Ajuste MF e toque várias vezes no visor: nenhum toast repetitivo deve aparecer.
- Volte a Foto, altere filmes, teste Flash e Noite novamente.
- Rotacione durante Pro e verifique restauração de estado e disponibilidade do sensor.

## Limites
Compilação Android não verifica responsividade visual nem desempenho óptico; testar num dispositivo físico. O vídeo foi utilizado apenas para inspeção; **não foi adicionado ao repositório**.
