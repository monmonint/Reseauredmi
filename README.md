# Mode 3G/4G

Application Android pour forcer le réseau mobile d’un **Xiaomi Redmi Note 10 Pro** (MIUI ou HyperOS) en **3G uniquement** ou en **4G (LTE préféré)**.

À la maison, le 3G passe souvent mieux, mais le téléphone se raccroche tout seul au 4G. Deux gros boutons règlent ça. L’écran affiche aussi le type de réseau actuel.

**Téléchargement direct de l’APK :**

https://github.com/monmonint/Reseauredmi/releases/download/latest/Mode-3G-4G.apk

La page de la version : https://github.com/monmonint/Reseauredmi/releases/tag/latest

L’APK est signé avec une clé de débogage **publique**, enregistrée dans le dépôt (`keystore/debug.keystore`, mot de passe `android`). Ce n’est pas une clé de production : elle sert uniquement à ce que chaque mise à jour s’installe par-dessus la précédente, sans désinstaller.

## Installer

1. Ouvrez le lien ci-dessus depuis le téléphone et téléchargez `Mode-3G-4G.apk`.
2. Ouvrez le fichier. Android demande d’autoriser les applications inconnues pour le navigateur ou « Téléchargements » : acceptez, puis installez.
3. MIUI / HyperOS peut afficher un avertissement parce que l’APK est une version de débogage. C’est normal pour cette application.
4. Au premier lancement, autorisez l’accès au téléphone : il sert seulement à afficher 2G, 3G ou 4G.

## Utiliser

- **3G** — WCDMA uniquement. Le téléphone ne cherche plus le 4G.
- **4G** — LTE préféré (LTE/GSM/WCDMA). Le 4G est utilisé quand il est bon, avec repli en 3G ou 2G.
- Le grand chiffre en haut est le réseau **réellement** utilisé. Il peut mettre quelques secondes à changer après un appui.
- S’il y a deux SIM, choisissez la ligne. Par défaut, c’est la SIM data.

## Shizuku (appui direct)

Une application normale n’a pas le droit `MODIFY_PHONE_STATE`, donc elle ne peut pas appeler `setAllowedNetworkTypesForReason` ni `setPreferredNetworkType`. [Shizuku](https://shizuku.rikka.app/) prête les droits du débogage Android (utilisateur shell), sans root.

Sur le Redmi Note 10 Pro :

1. **Paramètres → À propos du téléphone → Version MIUI** (ou HyperOS). Appuyez sept fois pour activer les options développeur.
2. **Paramètres → Paramètres supplémentaires → Options pour les développeurs.**
3. Activez **Débogage USB**.
4. Activez **Débogage USB (paramètres de sécurité)** s’il est proposé. Sans ça, MIUI bloque souvent Shizuku.
5. Activez **Débogage sans fil**. Le téléphone affiche une adresse, un port et un code d’appairage.
6. Installez Shizuku depuis le Play Store, ou depuis https://shizuku.rikka.app/download/ si le Play Store n’est pas là. Le bouton « Installer Shizuku » de l’application ouvre la même page.
7. Dans Shizuku : **Jumelage** avec le code, puis **Démarrer**.
8. Revenez dans **Mode 3G/4G** et appuyez sur **Autoriser Shizuku**.
9. Appuyez sur **3G** ou **4G**.

Shizuku lancé par le débogage sans fil s’arrête après un redémarrage. Il faut le redémarrer. Le mode réseau, lui, reste en général enregistré par Android. La case « Réappliquer au démarrage » ne peut agir que si Shizuku tourne déjà (par exemple avec le démarrage root). Sur MIUI, autorisez aussi le démarrage automatique de Shizuku et de cette application, et retirez-les de l’optimisation de batterie.

## Si l’appui direct ne suffit pas

Le bouton **Menu radio (secours)** ouvre l’écran caché équivalent à `*#*#4636#*#*` (`com.android.phone.settings.RadioInfo`).

Dans **Informations téléphone**, liste **Définir le type de réseau préféré** :

| Bouton | Entrée du menu (souvent en anglais) |
| --- | --- |
| 3G | `WCDMA only` |
| 4G | `LTE/GSM/WCDMA` |

Si l’écran ne s’ouvre pas, composez `*#*#4636#*#*` dans l’application Téléphone.

## Tuile des réglages rapides

1. Déroulez le panneau de notifications.
2. Modifiez les tuiles (icône crayon).
3. Ajoutez **Mode 3G/4G**.

Un appui bascule entre 3G et 4G. La tuile est allumée quand le 3G est forcé. Si Shizuku n’est pas prêt, l’appui ouvre l’application.

## Ce que fait vraiment l’application

Avec Shizuku, un petit service tourne avec l’identité shell et enchaîne trois méthodes, de la plus fiable à la plus ancienne :

1. L’API cachée du téléphone : `setAllowedNetworkTypesForReason` (raison utilisateur) et `setPreferredNetworkType` (mode RIL `2` = WCDMA only, mode `9` = LTE/GSM/WCDMA).
2. La commande shell `cmd phone set-allowed-network-types-for-users`, présente à partir d’Android 12. Le masque est envoyé en binaire, comme l’attend le système.
3. `settings put global preferred_network_mode`, utile surtout sur Android 11, où le téléphone surveille encore ce réglage.

Le Redmi Note 10 Pro est passé d’Android 11 à Android 13 / HyperOS. Les deux premières méthodes couvrent cette plage. Le menu radio reste là si MIUI refuse quand même la commande.

L’application ne demande pas le root et ne change pas le mode choisi par l’opérateur (raison « carrier »), seulement le choix utilisateur.
