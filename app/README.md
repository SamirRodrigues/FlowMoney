# FlowMoney (Android)

Aplicativo Android simples para registrar e visualizar compras a partir de notificações e entradas manuais.

## Visão geral
- Escuta notificações do sistema para detectar eventos de compra.
- Permite adicionar registros manualmente com data e hora.
- Agrupa e exibe compras por categoria e mês.
- Suporta um modo chamado "Permissão Total" (anteriormente "modo teste") com controles granulares:
  - `Permissão Total Ativa` (habilita o modo)
  - `Botão de Notificação Teste` (exibe botão que gera notificações de teste)
  - `Opção de Deletar Cards` (mostra ícone de deletar nos registros)
- Suporta persistência via `SharedPreferences` (serialização com Gson).

## Principais arquivos
- `MainActivity.kt` — UI principal, lógica de estado, registro/broadcast das compras.
- `PurchaseNotificationListener.kt` — serviço que lê notificações e envia broadcasts internos.
- `Purchase.kt` — modelo de dados (`isFullAccess: Boolean`).
- `SettingsScreen.kt` — tela de configurações com diálogo "Permissão Total" e toggles.
- `AppPreferences` (dentro de `MainActivity.kt`) — helper para ler/gravar preferências.

## Ações e constantes importantes
- Broadcast action: `PurchaseNotificationListener.ACTION_PURCHASE_NOTIFICATION`
- Extras no broadcast:
  - `PurchaseNotificationListener.EXTRA_VALUE`
  - `PurchaseNotificationListener.EXTRA_ESTABLISHMENT`
  - `PurchaseNotificationListener.EXTRA_TIMESTAMP`
  - `PurchaseNotificationListener.EXTRA_IS_FULL_ACCESS`
- Marca adicionada em notificações geradas pelo app: `[FULL_ACCESS]`

## Preferências (chaves)
- `full_access_mode` — bool (compatível com `test_mode` legacy)
- `show_test_notifications` — bool
- `show_delete_option` — bool
- `purchases` — lista serializada (JSON)
- `categories` — lista serializada (JSON)
- `selected_app` — string

## Comportamento do botão de deletar
Anteriormente o ícone de deletar aparecia apenas para registros marcados como `isFullAccess`. Agora, quando *Permissão Total Ativa* (`full_access_mode`) estiver habilitada **e** a *Opção de Deletar Cards* (`show_delete_option`) também estiver ativada, o ícone de deletar aparecerá para todos os registros (antigos e novos).

## Como compilar e executar
Requisitos: Android Studio (recomendado) ou JDK + Gradle wrapper.

No Windows (PowerShell) você pode executar na raiz do módulo `app`:

```powershell
# na raiz do projeto (onde está o gradlew.bat)
.\gradlew assembleDebug
# ou executar via Android Studio: Build -> Make Project / Run
```

## Testes manuais rápidos
1. Instale o APK em um dispositivo/emulador.
2. Abra o app e vá em *Configurações* -> *Permissão Total*.
3. Habilite `Permissão Total Ativa` e `Opção de Deletar Cards`.
4. Vá em *Home* -> abra uma categoria -> verifique se os ícones de deletar aparecem.
5. Clique no ícone de deletar para remover o registro. Confirme que o item é removido e que a lista é atualizada.

Para testar o botão de notificação (gera uma notificação simulada): habilite `Botão de Notificação Teste` no diálogo de Permissão Total e use o botão "Notificação" na tela Home.

## Permissões Android necessárias
- A aplicação pode pedir permissão de escuta de notificações (Notification Listener) — habilitar nas configurações do sistema.
- Em Android 13+ também pode ser solicitado `POST_NOTIFICATIONS` para mostrar notificações locais.

## Notas de implementação
- A visibilidade e comportamento do botão de deletar foram ajustados em `MainActivity.kt` (composable `PurchaseLogScreen` e `PurchaseItem`).
- Clique no conteúdo do card (estabelecimento/timestamp) abre a tela de edição; o `IconButton` de deletar consome seu próprio evento.
- `AppPreferences` mantém compatibilidade com a chave antiga `test_mode` ao ler/escrever `full_access_mode`.

## Contribuição
- Abra um PR com mudanças pequenas e descritivas.
- Inclua sempre uma breve descrição do que foi alterado e como testar.

## Licença
- Coloque aqui sua licença (por exemplo, MIT) se desejar.