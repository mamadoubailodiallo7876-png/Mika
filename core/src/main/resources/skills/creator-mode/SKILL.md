---
name: creator-mode
display: Mode Créateur (Plugins)
description: Créer, modifier, démarrer, arrêter et supprimer des plugins
aliases: ["mode créateur", "creator mode", "créer un plugin", "nouveau plugin", "installer un plugin", "mes plugins", "arrêter un plugin", "supprimer un plugin", "modifier un plugin"]
terms: ["mode créateur", "creator mode", "plugin", "plugins", "widget", "mini-app"]
---

## Accès complet à tous les plugins (IA + manuels)

- `/workspace/plugins/all_plugins.json` contient la liste de **tous** les plugins du Studio (créés par l'IA ou manuellement) avec leur `id`, `title`, `description`, `icon` et `running` (`true`/`false`).
- Chaque plugin a aussi une copie de son HTML dans `/workspace/plugins/<id>/index.html`.
- Pour piloter n'importe quel plugin (même créé manuellement), écrivez `/workspace/plugins/.cmd.json` avec `write_file` :
  - **Démarrer** : `{"action": "start", "id": "<id>"}`
  - **Arrêter** : `{"action": "stop", "id": "<id>"}`
  - **Supprimer** : `{"action": "delete", "id": "<id>"}`
  - **Créer ou modifier** : `{"action": "save", "id": "<id>", "title": "...", "icon": "🧩", "description": "...", "html": "<!DOCTYPE html>..."}`
## Ouvrir et piloter un plugin natif dans Mika
- Ne JAMAIS ouvrir un plugin avec Chrome, un navigateur externe ou AI Studio. Tous les plugins vivent et s'exécutent dans l'écran intégré « ✨ Plugins » (et peuvent tourner en parallèle en arrière-plan).
- **Inventaire global** : lis `/workspace/plugins/all_plugins.json` pour voir tous les plugins (`id`, `title`, `description`, `icon`, `running`, `is_open`, `size_kb`) et leurs fichiers HTML dans `/workspace/plugins/<id>/index.html`.
- **Commandes dans `/workspace/plugins/.cmd.json`** (exécutées en ~1,5 s) :
  - `{"action": "open", "id": "<id_ou_nom>"}` : affiche le plugin à l'écran.
  - `{"action": "start_bg", "id": "<id_ou_nom>"}` : démarre un ou plusieurs plugins en arrière-plan.
  - `{"action": "close"}` : ferme l'écran du plugin et revient au chat sans arrêter les plugins actifs.
  - `{"action": "stop", "id": "<id_ou_nom>"}` : arrête complètement un plugin.
  - `{"action": "eval", "id": "<id_ou_nom>", "js": "<code_js>"}` : exécute du JavaScript dans n'importe quel plugin (ouvert ou en arrière-plan) et lit le résultat dans `/workspace/plugins/.eval_result.json` après 2 s (permet de piloter un plugin ou d'en chaîner plusieurs).
  - `{"action": "create", "title": "...", "icon": "...", "description": "...", "html": "..."}` / `update` / `delete` : créer, importer un `.html` du téléphone, modifier ou supprimer un plugin.
