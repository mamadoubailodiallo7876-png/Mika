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
- Ne JAMAIS ouvrir un plugin avec Chrome, un navigateur externe ou AI Studio. Les plugins s'ouvrent uniquement dans l'écran intégré « ✨ Plugins » de Mika.
- Pour lister tous les plugins disponibles, lis `/workspace/plugins/all_plugins.json`.
- Pour OUVRIR un plugin directement à l'écran depuis le chat, écris ce JSON dans `/workspace/plugins/.cmd.json` :
- {"action": "open", "id": "<id_ou_nom_du_plugin>"}
- Mika ouvrira automatiquement l'écran ✨ Plugins et affichera ce plugin en moins de 2 secondes.
- Autres actions supportées dans `/workspace/plugins/.cmd.json` : `create`, `update`, `stop`, `delete`.
