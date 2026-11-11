# Déploiement gratuit (Oracle Cloud Always Free)

Ce guide met AtelierFlow en ligne en HTTPS, sur une VM gratuite qui exécute le même `docker compose` qu'en
local. L'application tourne en continu : WebSocket, tâches planifiées et simulateur de télémétrie fonctionnent
comme en local.

```text
Internet ──HTTPS──▶ Caddy (certificat Let's Encrypt automatique)
                      └──▶ nginx (SPA + proxy /api, /ws) ──▶ Spring Boot ──▶ PostgreSQL
```

Ce qui change par rapport à la démo locale (`docker-compose.prod.yml`) :

- **Caddy** termine le HTTPS et renouvelle seul le certificat ;
- le **cookie de rafraîchissement** passe en `Secure` ;
- la **clé JWT** et le **mot de passe PostgreSQL** sont générés sur le serveur, dans un fichier `.env` jamais
  commité ;
- les conteneurs **redémarrent automatiquement** après un reboot.

## 1. Créer le compte et la VM (une seule fois)

1. **Créer un compte Oracle Cloud** sur https://www.oracle.com/cloud/free/. Une carte bancaire est demandée pour
   vérifier l'identité ; les ressources « Always Free » ne sont pas facturées. Choisis une *home region* proche
   (Paris, Marseille, Francfort…). Ce choix est définitif.
2. **Créer une clé SSH** sur ton PC (si tu n'en as pas déjà une) :
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/oracle_atelierflow
   ```
3. **Créer l'instance** : *Compute → Instances → Create instance*.
   - **Image** : Canonical Ubuntu 24.04.
   - **Shape** : *Ampere* `VM.Standard.A1.Flex`, 2 OCPU et 12 Go de RAM (l'offre gratuite va jusqu'à 4 OCPU et
     24 Go). Évite la shape AMD « micro » (1 Go) : elle est trop petite pour Java + PostgreSQL + le build.
   - **SSH keys** : colle le contenu de `~/.ssh/oracle_atelierflow.pub`.
   - Garde l'**adresse IPv4 publique** attribuée automatiquement.

   Si Oracle répond *Out of capacity*, réessaie dans un autre *availability domain* ou un peu plus tard :
   c'est fréquent pour les instances Ampere gratuites.
4. **Ouvrir les ports 80 et 443** : *Networking → Virtual cloud networks →* ton VCN *→ Security Lists → Default
   → Add Ingress Rules*, deux règles TCP depuis `0.0.0.0/0`, ports de destination `80` puis `443`.

## 2. Obtenir un nom de domaine gratuit

Le certificat HTTPS exige un nom de domaine. Le plus simple :

1. Va sur https://www.duckdns.org et connecte-toi (avec ton compte GitHub par exemple).
2. Crée un sous-domaine, par exemple `atelierflow`, ce qui donne `atelierflow.duckdns.org`.
3. Renseigne l'**IP publique** de la VM et clique *update ip*.

## 3. Installer l'application sur la VM

```bash
ssh -i ~/.ssh/oracle_atelierflow ubuntu@<IP_PUBLIQUE>
```

```bash
git clone https://github.com/H-raf0/atelierflow.git && cd atelierflow
```

```bash
./deploy/install.sh atelierflow.duckdns.org
```

Le script `deploy/install.sh` fait le reste :

- il installe Docker ;
- il ouvre les ports 80/443 dans le pare-feu de la VM (les images Ubuntu d'Oracle n'autorisent que SSH) ;
- il génère les secrets dans `.env` ;
- il construit les images et démarre la pile.

Le premier build prend quelques minutes. L'application est ensuite disponible sur
`https://atelierflow.duckdns.org`, avec les comptes de démonstration du README.

## 4. Exploitation courante

Toutes les commandes se lancent depuis le dossier `atelierflow` sur la VM. Pour raccourcir :

```bash
alias dc='sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml'
```

| Besoin | Commande |
|---|---|
| Mettre à jour après un `git push` | `git pull && dc up -d --build --wait` |
| Voir les logs du backend | `dc logs -f backend` |
| État des conteneurs | `dc ps` |
| Sauvegarder la base | `dc exec postgres pg_dump -U atelierflow atelierflow > sauvegarde.sql` |
| Remettre les données de démo à zéro | `dc down -v && dc up -d --wait` |

**Les comptes de démo sont publics** : n'importe qui peut se connecter en administrateur et modifier les
données. Pour retrouver chaque semaine une démo propre, tu peux planifier une remise à zéro (`crontab -e`,
chaque lundi à 4 h) :

```text
0 4 * * 1 cd /home/ubuntu/atelierflow && docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```
