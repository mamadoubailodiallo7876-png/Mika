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
## Outils natifs pour piloter tous les plugins de Mika (comme le navigateur intégré)
Tu disposes de **6 outils natifs directs** pour voir, ouvrir et piloter n'importe quel plugin dans « ✨ Plugins » (un ou plusieurs en parallèle, à l'écran ou en arrière-plan). **N'utilise JAMAIS Chrome, le navigateur externe ni AI Studio pour un plugin Mika.**

1. **`plugin_list()`** : liste tous les plugins installés (`id`, `title`, `description`, `running`, `is_open`, `size_kb`). Commence toujours par `plugin_list` si tu ne connais pas l'identifiant exact.
2. **`plugin_open(id, background=false)`** : affiche un plugin à l'écran (ou le démarre silencieusement en arrière-plan avec `background=true`).
3. **`plugin_read(id)`** : inspecte l'interface vivante d'un plugin (texte visible, boutons cliquables, champs et listes avec leurs sélecteurs CSS — exactement comme `ReadPage` du navigateur).
4. **`plugin_interact(id, action, selector, text)`** : clique sur un bouton (`action="click"`, par sélecteur CSS ou texte visible), tape dans un champ (`action="type"`) ou choisit une option (`action="select"`).
5. **`plugin_eval(id, js)`** : exécute du JavaScript directement dans n'importe quel plugin (ouvert ou en arrière-plan) et retourne le résultat immédiatement — idéal pour lire un état complexe, déclencher une fonction interne ou chaîner plusieurs plugins entre eux.
6. **`plugin_manage(action, id, title, icon, description, html)`** : gère le cycle de vie (`close` pour revenir au chat sans arrêter les plugins actifs, `stop` pour arrêter un plugin, `create` / `update` / `delete` pour créer, importer un `.html`, modifier ou supprimer un plugin).
