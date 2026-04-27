# MC01 Pilot Assistant

Projeto Android Studio em Kotlin + Jetpack Compose, focado em tablet e responsivo no celular.

## Módulos

1. **Checklists**: carrega `app/src/main/assets/checklists/mc01_checklist.json`. Edite esse JSON para inserir o checklist oficial do MC01.
2. **Cartas**: permite subir PDFs, imagens e textos, organizar por pasta e abrir com visualizador externo do Android.
3. **Documentos**: mesma lógica de biblioteca de arquivos, separada das cartas.
4. **Anotações**: notas via teclado salvas em `.txt` e desenho livre salvo em `.png`.

## Split screen

Em telas com largura >= 700dp, o app mostra dois módulos ao mesmo tempo. Arraste o divisor central para mudar o tamanho de cada painel.
Em celular, ele alterna entre módulos com chips no topo.

## Como abrir no Android Studio

1. Extraia o ZIP.
2. Abra a pasta `MC01PilotAssistant` no Android Studio.
3. Aguarde o Gradle Sync.
4. Rode em um tablet/emulador Android.

## Observação importante

O PDF anexado não ficou disponível no ambiente de geração. Por isso, deixei um checklist inicial genérico e seguro como placeholder. Substitua o conteúdo do JSON pelos itens oficiais do PDF/manual do MC01 antes de usar em voo.
