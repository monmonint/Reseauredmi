# Mode 3G/4G

Application Android pour forcer le réseau mobile d’un **Xiaomi Redmi Note 10 Pro** (MIUI ou HyperOS) en **3G uniquement** ou en **4G (LTE préféré)**.

À la maison, le 3G passe souvent mieux, mais le téléphone se raccroche tout seul au 4G. Deux gros boutons règlent ça. L’écran affiche aussi le type de réseau actuel.

**Téléchargement direct de l’APK :**

https://github.com/monmonint/Reseauredmi/releases/download/latest/Mode-3G-4G.apk

La page de la version : https://github.com/monmonint/Reseauredmi/releases/tag/latest

Le dépôt est privé : il faut être connecté à GitHub pour télécharger l’APK.

L’APK est signé avec une clé de débogage **publique**, enregistrée dans le dépôt (`keystore/debug.keystore`, mot de passe `android`). Ce n’est pas une clé de production : elle sert uniquement à ce que chaque mise à jour s’installe par-dessus la précédente, sans désinstaller. Empreinte SHA-256 du certificat : `f54e028eeb962928a7454db4e1566e0d82e67114bd0befa58605da78f2e96255`.

## Installer

1. Ouvrez le lien ci-dessus depuis le téléphone et téléchargez `Mode-3G-4G.apk`.
2. Ouvrez le fichier. Android demande d’autoriser les applications inconnues pour le navigateur ou « Téléchargements » : acceptez, puis installez.
3. MIUI / HyperOS peut afficher un avertissement parce que l’APK est une version de débogage. C’est normal pour cette application.
4. Au premier lancement, autorisez l’accès au téléphone : il sert seulement à afficher 2G, 3G ou 4G, et à choisir la SIM.

## Utiliser

- **3G** — WCDMA uniquement. Le téléphone ne cherche plus le 4G.
- **4G** — LTE préféré (LTE/GSM/WCDMA). Le 4G est utilisé quand il est bon, avec repli en 3G ou 2G. Pas de 5G : ce téléphone n’en a pas.
- Le grand chiffre en haut est le réseau **réellement** utilisé. Il peut mettre quelques secondes à changer après un appui.
- S’il y a deux SIM, choisissez la ligne. Par défaut, c’est la SIM data.

Chaque appui ouvre une connexion ADB, envoie la commande, puis la referme. Il n’y a pas de service qui reste actif en arrière-plan.

## Préparer l’accès local

Une application normale n’a pas le droit `MODIFY_PHONE_STATE`. L’application embarque donc un client ADB ([libadb-android](https://github.com/MuntashirAkon/libadb-android)). Après une préparation, elle parle au démon `adbd` du téléphone sur `127.0.0.1:5555`. Cette connexion passe par la boucle locale : **elle n’a pas besoin d’un réseau Wi-Fi ni d’un ordinateur**.

Le Wi-Fi peut rester allumé sans être connecté à un réseau. Il peut aussi être coupé. Voir les limites plus bas : ce comportement n’a pas été exécuté sur un Redmi dans l’environnement de développement, et MIUI peut s’écarter d’Android.

La préparation est à refaire **après chaque redémarrage**. Android mémorise le port dans `service.adb.tcp.port`, qui n’est pas une propriété persistante. La clé RSA de l’application, elle, reste dans le téléphone.

### Avec le débogage sans fil

Le téléphone doit être **connecté à un Wi-Fi** pendant cette étape seulement. Le débogage sans fil s’arrête sinon.

1. **Paramètres → À propos du téléphone → Version MIUI** (ou HyperOS). Appuyez sept fois pour activer les options développeur.
2. **Paramètres → Paramètres supplémentaires → Options pour les développeurs.**
3. Activez **Débogage USB**.
4. Activez **Débogage USB (paramètres de sécurité)** s’il est proposé. Sans ça, MIUI refuse souvent les commandes shell.
5. Activez **Débogage sans fil**.
6. Ouvrez **Appairer l’appareil avec un code d’association** et **laissez cette fenêtre ouverte**.
7. Dans **Mode 3G/4G**, **Préparer l’accès local** : recopiez seulement le code à 6 chiffres, puis **Appairer avec ce code**.

L’application cherche elle-même les deux ports en mDNS (`_adb-tls-pairing._tcp` pour l’appairage, `_adb-tls-connect._tcp` pour la connexion). Le port d’appairage n’existe que tant que la fenêtre du code est ouverte.

Si la recherche ne trouve rien, saisissez les ports à la main **sans les inverser**. Les deux écrans affichent « Adresse IP & Port », mais ce n’est pas le même nombre :

| Champ | Où le lire |
| --- | --- |
| Port d’appairage | Fenêtre **Associer avec l’appareil**, à côté du code. Exemple : `192.168.1.12:37527` → **37527**. |
| Port de connexion | Écran principal **Débogage sans fil**, sous l’interrupteur. Exemple : `192.168.1.12:42841` → **42841**. |

L’application s’appaire, se connecte au port chiffré, envoie l’équivalent de `adb tcpip 5555`, puis vérifie que `127.0.0.1:5555` répond. Le débogage sans fil peut ensuite s’afficher comme arrêté : c’est normal, le port fixe 5555 a pris le relais.

### Avec un ordinateur

1. Activez le débogage USB, branchez le câble, et acceptez l’ordinateur.
2. Sur l’ordinateur : `adb tcpip 5555`
3. Dans l’application : **Vérifier le port local**.
4. Si le téléphone affiche **Autoriser le débogage USB**, cochez **Toujours autoriser depuis cet ordinateur**, puis acceptez. La clé affichée est celle de l’application, pas celle du PC.

Le câble et le PC ne servent plus ensuite, jusqu’au redémarrage.

### Ensuite

Coupez le Wi-Fi (ou déconnectez-le du réseau) et appuyez sur **3G** ou **4G**. L’application se connecte à `127.0.0.1:5555`, lance la commande, puis se déconnecte.

Pour vérifier que le mode a bien changé, regardez le grand chiffre en haut. Le journal technique (bouton « Détails techniques ») contient `via=adb` et la réponse de `cmd phone`.

## Limites

- **Redémarrage.** Le port 5555 se ferme. Il faut refaire la préparation. La clé d’appairage reste en place : au prochain passage par le débogage sans fil, le code sert à rouvrir le port, pas à recréer la confiance. Ce saut demande encore un Wi-Fi, le temps d’envoyer `tcpip:5555`.
- **Réseau local.** `adb tcpip 5555` fait écouter `adbd` sur **toutes** les interfaces (`0.0.0.0:5555`), pas seulement sur `127.0.0.1`. Tant que le téléphone est associé à un Wi-Fi, une autre machine du réseau peut tenter de se connecter, et le protocole de ce port n’est pas chiffré. Une fois le Wi-Fi déconnecté, seule la boucle locale y accède. Il n’existe pas d’option standard pour n’écouter que sur `127.0.0.1`. Ne laissez pas ce port ouvert sur un réseau dont vous n’avez pas confiance.
- **MIUI.** Certains téléphones Xiaomi coupent `adbd` quand le Wi-Fi tombe, ou quand « Débogage USB (paramètres de sécurité) » est désactivé. Si les boutons échouent sans Wi-Fi alors que la préparation venait de réussir, c’est cette limite. Le menu radio reste utilisable.
- **Pas d’essai sur un Redmi ici.** Le comportement « Wi-Fi déconnecté, `127.0.0.1:5555` répond » s’appuie sur le code d’Android (socket d’écoute sur toutes les interfaces après `tcpip`) et sur des retours publics (débogage local encore vivant Wi-Fi coupé, jusqu’au redémarrage). Il n’a pas été exécuté sur un Redmi Note 10 Pro, ni sur un autre téléphone, dans l’environnement qui a construit cette application.
- **Android 11.** `cmd phone set-allowed-network-types-for-users` date d’Android 12. Sur Android 11, l’application écrit quand même `settings put global preferred_network_mode`. L’écran dit alors que le réglage est envoyé, pas que le modem l’a confirmé. Le menu radio est le recours sûr.
- **Fenêtre d’autorisation.** La première connexion classique peut demander « Autoriser le débogage USB », surtout après `adb tcpip` lancé depuis un PC. L’appairage sans fil enregistre en principe la clé sans cette fenêtre. Cochez toujours autoriser.

## Secours

### Shizuku

[Shizuku](https://shizuku.rikka.app/) reste disponible, il n’est plus nécessaire. S’il tourne et que le port local est fermé, les boutons passent par Shizuku. Démarrez-le avec le débogage sans fil, puis **Autoriser Shizuku**. Sur Xiaomi, le même « Débogage USB (paramètres de sécurité) » est requis. Shizuku lancé ainsi s’arrête aussi après un redémarrage.

La case « Réappliquer au démarrage » ne concerne que Shizuku. Le port ADB local est déjà fermé à ce moment-là, l’application ne tente pas de le rouvrir toute seule.

### Menu radio

**Menu radio (secours)** ouvre l’écran caché équivalent à `*#*#4636#*#*` (`com.android.phone.settings.RadioInfo`).

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

Un appui bascule entre 3G et 4G. La tuile est allumée quand le 3G est forcé. Sans port local et sans Shizuku, l’appui ouvre l’application.

## Ce que fait vraiment l’application

Avec le port local, ou avec Shizuku, la commande est celle du shell Android :

```text
cmd phone set-allowed-network-types-for-users -s EMPLACEMENT MASQUE_BINAIRE
```

Le masque est en binaire, comme le système le lit. L’emplacement est celui de la SIM, pas l’identifiant d’abonnement.

| Bouton | Mode RIL | Masque |
| --- | --- | --- |
| 3G | 2, WCDMA only | UMTS, HSDPA, HSUPA, HSPA, HSPAP (`17284`, binaire `100001110000100`) |
| 4G | 9, LTE/GSM/WCDMA | le masque 3G plus GSM, GPRS, EDGE, LTE et LTE_CA (`316295`), sans 5G |

L’application relit ensuite le masque. S’il correspond, le changement est confirmé. Sinon elle l’indique, même si la commande a répondu « completed ». Elle écrit aussi `settings put global preferred_network_mode` (et `preferred_network_mode` suivi de l’identifiant d’abonnement, sans tiret bas). Sur Android 12 et plus, ce réglage seul ne suffit pas : l’écran ne le présente comme confirmé que si le masque ou la commande shell le dit.

Shizuku, en plus, appelle les API cachées `setAllowedNetworkTypesForReason` et `setPreferredNetworkType` depuis un processus shell. Le client ADB intégré ne le fait pas : il n’a que le shell.

L’application ne demande pas le root et ne change pas le mode choisi par l’opérateur, seulement le choix utilisateur.

Le client ADB est libadb-android 3.1.1 (licence GPL-3.0 ou Apache-2.0, utilisée ici sous Apache-2.0) avec Conscrypt pour le chiffrement du débogage sans fil. La clé privée reste dans le stockage privé de l’application et n’est pas incluse dans les sauvegardes Android.
