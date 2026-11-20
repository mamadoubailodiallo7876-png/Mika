#!/usr/bin/env python3
import os, re, shutil

# 1. Supprimer les dossiers de ressources chinois
for d in ["app/src/main/res/values-zh", "app/src/main/res/values-b+zh+Hant"]:
    if os.path.isdir(d):
        shutil.rmtree(d)

# 2. Traductions exactes (du plus long au plus court)
REPLACEMENTS = [
    ("模型账户额度已用完,请检查服务商账户的余额或套餐。", "Le quota du compte du modèle est épuisé. Vérifiez le solde ou le forfait de votre fournisseur."),
    ("模型账户额度已用完，请检查服务商账户的余额或套餐。", "Le quota du compte du modèle est épuisé. Vérifiez le solde ou le forfait de votre fournisseur."),
    ("请检查服务商账户的余额或套餐。", "Vérifiez le solde ou le forfait de votre fournisseur."),
    ("请检查服务商账户的余额或套餐", "Vérifiez le solde ou le forfait de votre fournisseur"),
    ("模型账户额度已用完", "Le quota du compte du modèle est épuisé"),
    ("模型账户余额不足", "Solde du compte du modèle insuffisant"),
    ("模型请求频率超限", "Limite de requêtes du modèle dépassée"),
    ("模型请求超时", "Délai d'attente du modèle dépassé"),
    ("模型服务暂时不可用", "Service du modèle temporairement indisponible"),
    ("模型返回为空", "Le modèle a renvoyé une réponse vide"),
    ("网络连接失败", "Échec de la connexion réseau"),
    ("请求失败", "Échec de la requête"),
    ("关于 Mika", "À propos de Mika"),
    ("智谱 Coding Plan", "Zhipu Coding Plan"),
    ("通义千问", "Qwen (Alibaba)"),
    ("硅基流动", "SiliconFlow"),
    ("火山方舟", "Volcengine Ark"),
    ("阶跃星辰", "StepFun"),
    ("腾讯混元", "Tencent Hunyuan"),
    ("百度千帆", "Baidu Qianfan"),
    ("智谱", "Zhipu GLM"),
    ("任务已停止", "Tâche arrêtée"),
    ("任务已完成", "Tâche terminée"),
    ("任务未完成", "Tâche non terminée"),
    ("正在思考", "Réflexion en cours"),
    ("正在执行", "Exécution en cours"),
    ("等待确认", "En attente de confirmation"),
    ("已取消", "Annulé"),
    ("未知错误", "Erreur inconnue"),
    ("简体中文", "Chinois (désactivé)"),
    ("繁體中文", "Chinois trad. (désactivé)"),
]

for base in ["app/src/main/java", "core/src/main/kotlin", "app/src/main/res"]:
    for r, _, files in os.walk(base):
        for fn in files:
            if not (fn.endswith(".kt") or fn.endswith(".xml")):
                continue
            path = os.path.join(r, fn)
            txt = open(path, "r", encoding="utf-8").read()
            new_txt = txt
            for zh, fr in REPLACEMENTS:
                if zh in new_txt:
                    new_txt = new_txt.replace(zh, fr)
            if new_txt != txt:
                open(path, "w", encoding="utf-8").write(new_txt)

# 3. Imposer la langue française dans prompts/system/identity.md
id_path = "core/src/main/resources/prompts/system/identity.md"
if os.path.isfile(id_path):
    ident = open(id_path, "r", encoding="utf-8").read()
    rule = "\n- Langue obligatoire : répondez TOUJOURS en français dans tous vos messages, résumés et erreurs. N'utilisez jamais le chinois.\n"
    if "Langue obligatoire" not in ident:
        open(id_path, "w", encoding="utf-8").write(ident.rstrip() + rule)

print("French-only cleanup complete.")
