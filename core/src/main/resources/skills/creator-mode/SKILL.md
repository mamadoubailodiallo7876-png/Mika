---
name: creator-mode
display: Mode Créateur (Plugins)
description: Créer et installer des plugins dans le Studio Mika
aliases: ["mode créateur", "creator mode", "créer un plugin", "nouveau plugin", "installer un plugin", "mes plugins"]
terms: ["mode créateur", "creator mode", "plugin", "plugins", "widget", "mini-app"]
---

## Objectif

Créer, modifier, lister ou supprimer des plugins HTML/CSS/JS interactifs qui apparaissent directement dans le bouton **✨ Plugins** de la barre latérale de Mika.

## Où installer les plugins

- Chaque plugin est un dossier `/workspace/plugins/<id>/index.html` (avec `id` en minuscules, chiffres et tirets).
- Les métadonnées sont enregistrées dans `/workspace/plugins/registry.json` sous forme de tableau JSON d'objets `{"id": "...", "title": "...", "description": "...", "icon": "🧩"}`.
- Tout plugin écrit dans `/workspace/plugins/<id>/index.html` est détecté automatiquement par l'écran **✨ Plugins** de la barre latérale (aucun serveur requis, `localStorage` persistant).

## Étapes pour créer un plugin

1. Écrivez le code HTML/CSS/JS complet, autonome et adapté au mobile dans `/workspace/plugins/<id>/index.html` avec `write_file`.
2. Mettez à jour `/workspace/plugins/registry.json` (via `run_command` en Python ou `write_file`) pour ajouter l'entrée `{id, title, description, icon}` du plugin.
3. Confirmez à l'utilisateur qu'il peut ouvrir le plugin depuis **✨ Plugins** dans la barre latérale de Mika.
