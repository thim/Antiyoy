# Antiyoy — port Kotlin Multiplatform (KorGE)

Port do Antiyoy (libGDX/Java, pasta `core/` na raiz do repositório) para Kotlin Multiplatform,
com renderização em [KorGE](https://korge.org) e saída para web (JS e WebAssembly) e JVM.

## Estrutura

| Módulo | Conteúdo |
| --- | --- |
| `core/` | Lógica pura em `commonMain`: campo hexagonal, províncias, economia, unidades, combate, turnos, gerador de mapas, IA (fácil, normal, difícil, especialista, balanceador), desfazer. Sem dependência de renderização. |
| `src/` | App KorGE: menu, tela do jogo, câmera (arrastar/zoom/pinça), HUD, sons. |
| `resources/` | Sprites, fonte e sons do jogo original (PNGs re-codificados: os originais têm um chunk EXIF inválido). |
| `web/` | `index.html` copiado para o build Wasm (o KorGE não gera um). |

Mapeamento das classes originais:

| Original (Java) | Port (Kotlin) |
| --- | --- |
| `Hex`, `Unit`, `Province`, `Obj`, `GameRules` | `Hex`, `GameUnit`, `Province`, `Obj`, `Rules` |
| `FieldManager` + `GameController` + `RulesetGeneric` | `Game` (+ `Field` para a geometria) |
| `MapGenerator` + `MapGeneratorGeneric` | `MapGeneratorGeneric` |
| `MoveZoneDetection` | `MoveZoneDetection` |
| `SelectionManager` | `PlayerController` |
| `SnapshotManager` | `Snapshot` |
| `ai/*GenericRules`, `AiEasy` | `ai/AiLevels.kt` |

## Comandos

```bash
./gradlew :core:jvmTest                 # testes da lógica
./gradlew jsBrowserDistribution         # web (JS)    -> build/dist/js/productionExecutable
./gradlew wasmJsBrowserDistribution     # web (Wasm)  -> build/dist/wasmJs/productionExecutable
./gradlew runJvm                        # desktop
```

Os diretórios de distribuição são estáticos: sirva com qualquer servidor HTTP
(ex.: `python3 -m http.server` dentro da pasta).

## O que já foi portado

- Regras "genéricas" do Antiyoy completas: renda, impostos, fazendas, torres, torre forte,
  árvores que se espalham, túmulos, falência, unidades isoladas, fusão de unidades, divisão e união de províncias.
- Gerador de mapas aleatório (4 tamanhos, 2–7 cores).
- IA nos 5 níveis.
- Hotseat (vários humanos no mesmo aparelho) e modo só IA.
- Desfazer jogadas dentro do turno.

## Ainda não portado

Regras Slay, diplomacia, névoa de guerra, editor de mapas, campanha, fases de usuário, replays,
tutorial, salvar/carregar, idiomas (a interface está em pt-BR), nomes de cidades.
Android e iOS: basta habilitar `targetAndroid()`/`targetIos()` em `build.gradle.kts`
numa máquina com os SDKs.

O jogo original não permite uso comercial; este port herda a mesma restrição.
