# Sandevistan (Cyberpunk: Edgerunners) — Minecraft Java 1.21.1 / NeoForge 21.1.172

Este mod adiciona **somente** o Sandevistan. / This mod adds **only** the Sandevistan.

## Como compilar (GitHub Actions)
1. Crie um repositório e envie estes arquivos (a pasta `.github/workflows` inclusa já faz o build).
2. Aba **Actions** → rode o workflow → baixe o artefato `sandevistan-mod` (o `.jar` fica dentro).
3. Coloque o `.jar` na pasta `mods` do Minecraft 1.21.1 com NeoForge.

## Como obter
**Crafting** (mesa de trabalho, coluna do meio): **Neuralink** em cima, **Espinha** no meio, **Acelerador** embaixo.
Receitas das peças (ajustáveis em `data/sandevistan/recipe`):
- **Chip de Circuito**: Esmeralda e Redstone em cima; Barra de ferro e Pepita de ouro embaixo (2x2).
- **Neuralink**: Chip, Redstone, Chip em uma linha.
- **Espinha**: 3 Chips em coluna.
- **Acelerador**: Chip no centro, Diamante à esquerda e à direita, Barra de ouro em cima e embaixo.
Tudo também está na aba criativa "Sandevistan".

## Implante
- Clique com o botão direito segurando o item. Aparece um **aviso**: o implante causa **3 corações de dano instantâneo**.
- Clique de novo em até 10 segundos para confirmar. Precisa de mais de 3,5 corações de vida (criativo ignora).
- Depois de implantado, a coluna verde fica **nas suas costas permanentemente** (visível para todos os jogadores).
- Só sai se você remover o implante na aba **J** (botão "Remover implante", com o Sandevistan desligado): o item volta para o inventário. Morrer não remove o implante.

## Uso
| Tecla (configurável em Controles) | Ação |
|---|---|
| **V** | Ligar/desligar o Sandevistan (espera de 2 s entre trocas) |
| **J** | Abrir a aba do Sandevistan |

Enquanto está ligado:
- O jogo inteiro roda a **3 ticks por segundo** (igual a `/tick rate 3`, 85% mais lento).
- Você ganha o **efeito de Velocidade** (nível 55, aparece nos efeitos), pulo alto (~3 blocos), gravidade reduzida, queda rápida, Força IV, Resistência IV, Pressa III, Visão Noturna, bater/quebrar rápido como se o tempo fosse normal e **nenhum dano de queda**.
- A tela fica **levemente esverdeada** e silhuetas (afterimages) do jogador ficam paradas por onde ele passou, **cada uma dura 3 segundos** e muda suavemente de cor entre verde, azul e roxo.
- Ao desligar, o jogador **para no lugar** na hora (sem sair disparado).
- Sons: "Sandevistan Usa" ao ligar, "Sandevistan Para De Usar" ao desligar (os sons acompanham o jogador).

## Mente
- A barra **Mente** (HUD e aba J) é o tempo de uso: cheia dura **30 segundos reais** de uso (a contagem é em tempo real, mesmo com o jogo a 3 ticks).
- Desligado, ela recupera 1 segundo de uso a cada 3 segundos parados (do zero ao cheio em **90 segundos**). Precisa de pelo menos 10 de Mente para ligar.
- Se a Mente chegar a zero o Sandevistan desliga sozinho e o jogador sofre **sobrecarga** (náusea, fraqueza e lentidão).
- A posição do HUD da Mente muda na aba J (4 cantos; fica salva em `config/sandevistan-client.properties`).

## Ajustes de balanceamento
Tudo fica no topo de `SandevistanLogic.java` (velocidade, pulo, gravidade, força, pressa) e em `SandevistanData.java`
(duração da Mente, regeneração, tick rate). Textos em `assets/sandevistan/lang`.

## Idiomas
Português (pt_br e pt_pt) para quem joga em português; inglês para todos os outros idiomas.

## Multiplayer
O tick rate é do servidor inteiro: enquanto qualquer jogador está com o Sandevistan ligado, todos ficam a 3 ticks.

## Ciberpsicose (3.1.0)

- A cada 50% de Mente usada somam-se 5 de ciberpsicose; os sintomas aparecem na hora (com a Mente em 0 usando o Sandevistan, soma 2 de ciberpsicose por segundo).
- A perda de Mente da ciberpsicose é permanente: a Mente máxima cai 1 (100 vira 99) a cada 30/15/7/5/1 s conforme a fase. A parte perdida aparece em vermelho escuro na barra.
- Estabilizador Neural (mel, olho de aranha fermentado, açúcar e fungo do Nether): cura 5 de Mente na hora e dá Mente Boa por 1 minuto, em que cada segundo tem 1% de chance de curar 1 de Mente (recupera Mente perdida de vez, sem passar de 100) e baixa a ciberpsicose; tomar outro refaz o minuto e cura de novo.
- Imunossupressor (garrafa, lágrima de ghast, ametista e redstone): +20 de Mente por 1 minuto. Acumula (cada dose soma +20 e 1 minuto, até 100 de Mente no total; com 100 não dá para tomar). Ao acabar some todo o bônus e cada dose tira 5 de Mente para sempre.
- O ataque involuntário tem dano e alcance reduzidos (3/4/6 de dano, 2,5 blocos).

- Mente em 0: ao chegar em 0 o jogador toma 3 corações de dano (uma vez; só de novo depois de recuperar Mente e zerar outra vez) e, enquanto usa o Sandevistan com a Mente em 0, toma 1 coração a cada 5 s. O dano ignora a Resistência do próprio Sandevistan.

## Ciberpsicose: efeitos extras (3.1.0)

- Imunossupressor: também tira até 40 de ciberpsicose; quando o efeito acaba, essa mesma quantidade volta. Cores: Imunossupressor verde, Estabilizador Neural roxo.
- A partir de 100% a ciberpsicose só sobe (+1 a cada 1,5 s); esperar ou o Estabilizador não baixam, só o Imunossupressor alivia.
- Acima de 50%: a visão vira para o lado, o jogador anda ou pula sozinho, e há apagões (tela preta) em que ele caminha até a entidade mais próxima (jogador ou mob) e bate nela. Tudo cresce com a ciberpsicose.
- Sons estranhos só depois de 25%. Ícone de glitch só depois de 50%, pulando de lugar. Feixes de glitch: raros até 25%, cada vez mais até 75%, sempre na tela depois.
- Vultos escuros de jogadores aparecem ao redor (raros até 25%, comuns a partir de 50%) e somem 3 s depois de o jogador olhar para eles.
- Músicas (a partir de 75%, volume sobe até 100% e depois fica estável; só o jogador escuta; botão na aba do Sandevistan para trocar entre as duas ou nenhuma): "I Really Want to Stay at Your House" e "Żurawie", fornecidas pelo autor do mod; os direitos pertencem aos artistas.

## 3.1.1

- O apagão dura 10 segundos logo acima de 50% de ciberpsicose e aumenta com ela até 30 segundos (a partir de 150%).

## 3.1.2

- Tick rate do Sandevistan: 3 (85% mais lento que 20), velocidade e quebra de blocos ajustadas para o novo ritmo.
- Rápido também na água, debaixo d'água, agachado, em alma de areia/mel, em escadas/trepadeiras e em teia, arbusto de frutas e neve em pó.
- Comando de OP: `/sandevistan mente <add|remove|set> <0-100> [jogadores]` muda a Mente permanente.
- A tela não pisca em verde até 15% de ciberpsicose. A partir de 100% o jogador fica sempre em apagão (às vezes enxerga, mas não controla).
- A música da ciberpsicose para a música do jogo e o volume máximo foi reduzido.

## 3.1.3 (crafting mais difícil e Clínica de Ripperdoc)

- Cristal Neural: ametista + lápis-lazúli + redstone (sem forma). Fibra Sináptica: 2 linhas + bola de slime + esmeralda (sem forma).
- Neuralink: chip, Cristal Neural, chip em cima e Fibra Sináptica embaixo no meio.
- Ventoinha: 4 barras de ferro nos cantos + 1 pepita de ferro no meio. Núcleo: 3 barras de cobre (cima, esquerda, direita) + Ventoinha no meio + grade de ferro embaixo.
- Bateria de Energia: 4 ametistas e 4 barras de cobre alternados em volta de 1 bloco de redstone.
- Espinha: chip em cima, Núcleo no meio e Bateria de Energia embaixo.
- Servo-Motor: pistão + redstone + 2 barras de cobre (sem forma). Acelerador: o diamante da esquerda vira Servo-Motor.
- Clínica de Ripperdoc (bloco): 6 barras de ferro nas laterais, painel de vidro em cima, chip no meio e bloco de ferro embaixo. O Sandevistan só é implantado nela: segure o item, clique na clínica, pague 8 esmeraldas e confirme (continua dando 3 corações de dano).
- A gravidade ao cair com o Sandevistan ligado ficou um pouco menor; vultos um pouco mais frequentes.

## 3.2.0

- Dois sons de psicose do mod entram no sorteio dos sons estranhos (os sons do Minecraft continuam). O som "Alert" toca, só para o jogador, nos avisos em vermelho do chat.
- Ripperdoc: aldeão doutor (skin própria) que trabalha na Clínica de Ripperdoc e aparece numa garagem decorada nas vilas (as 5 variantes). Clique nele: ele pergunta se quer implantar o Sandevistan por 8 esmeraldas (botão no chat; gasta o item e causa 3 corações). Remover o implante também só com ele. OP: `/sandevistan clinica` constrói a garagem na sua frente.
- Sons de psicose e Alert agora seguem o jogador. Ligar/desligar sem cooldown; o som de um corta o do outro (sobreposição).
- Apagão: chance minúscula em 50%, bem mais fácil depois de 75%, comum a partir de 80% e bem comum a partir de 90%.
- Dano da ciberpsicose só a partir de 175% (1 coração/s) e 3 corações/s a partir de 225%.
- Tremedeira do modelo (rara, aumenta até 99%) e glitches no modelo visíveis para todos, por faixa: 1-25 fracos na cabeça, 26-50 também no corpo às vezes, 51-75 fracos ou fortes, 76-99 mais frequentes, 100+ vários e aleatórios.
- Comando `/sandevistan mente atual add|remove|set <valor> [jogadores]` muda a Mente que regenera.
- Usar sem Mente: 2 corações a cada 8 s de uso (o tempo acumulado se mantém ao parar). Acima de 100% o implante liga/desliga sozinho (5 a 10 s) e não há mais esse dano.
- Afterimages: só enquanto ligado, sem sumir sozinhas; ao desligar viram um feixe branco que some bem rápido.
- Receitas liberadas ao pegar qualquer ingrediente.
