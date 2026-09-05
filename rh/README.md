# RH — Application de gestion des ressources humaines

Application web Spring Boot (4.1.1) permettant de :
- s'authentifier (Spring Security, formulaire de connexion) ;
- enregistrer des agents ;
- affecter un agent, jour par jour, à un ou plusieurs sites ou à un autre lieu (texte libre) ;
- consulter un tableau de bord filtrable sur une plage de dates ;
- exporter les affectations vers un fichier Excel (.xlsx).

La base de données est **Turso** (libSQL), interrogée via JDBC.

## Pile technique

| Composant | Choix |
|---|---|
| Framework | Spring Boot 4.1.1 (Java 17) |
| Vue | Thymeleaf + Bootstrap 5 (CDN) |
| Sécurité | Spring Security (formulaire de connexion, mots de passe BCrypt) |
| Accès données | Spring Data JPA + Hibernate (`hibernate-community-dialects`, `SQLiteDialect`) |
| Base de données | Turso / libSQL via le pilote JDBC `com.dbeaver.jdbc.driver.libsql` |
| Export Excel | Apache POI (`poi-ooxml`) |

## Arborescence

```
rh/
├── pom.xml
└── src/main/
    ├── java/com/mahefa/rh/
    │   ├── RhApplication.java
    │   ├── config/SecurityConfig.java
    │   ├── model/            (Utilisateur, Agent, Site, Affectation)
    │   ├── repository/       (interfaces Spring Data JPA)
    │   ├── security/UtilisateurDetailsService.java
    │   ├── controller/       (Auth, Agent, Site, Affectation, Dashboard, Export)
    │   └── service/ExcelExportService.java
    └── resources/
        ├── application.properties
        ├── schema.sql   (création des tables si absentes)
        ├── data.sql     (utilisateur admin par défaut + sites de démo)
        ├── templates/   (pages Thymeleaf)
        └── static/css/style.css
```

## Configuration de la connexion à Turso

Turso expose une URL au format `libsql://<nom-de-la-base>.turso.io`, mais le pilote
JDBC utilisé (`dbeaver-jdbc-libsql`, celui embarqué dans DBeaver/CloudBeaver) attend
le format suivant :

```
jdbc:dbeaver:libsql:https://<nom-de-la-base>.turso.io
```

Le jeton d'authentification Turso se transmet comme **mot de passe** JDBC ; le nom
d'utilisateur reste vide.

Dans `application.properties` :

```properties
spring.datasource.driver-class-name=com.dbeaver.jdbc.driver.libsql.LibSqlDriver
spring.datasource.url=jdbc:dbeaver:libsql:https://rh-mahefa.aws-us-west-2.turso.io
spring.datasource.username=
spring.datasource.password=${TURSO_AUTH_TOKEN:}
```

**Ne mettez jamais le jeton en clair dans le fichier.** Définissez-le comme variable
d'environnement avant de lancer l'application :

```bash
export TURSO_AUTH_TOKEN="votre_jeton_turso"
```

Pour générer un jeton (via le CLI Turso) :

```bash
turso db tokens create rh-mahefa
```

## Compilation et lancement

> Le sandbox utilisé pour générer ce projet n'a pas d'accès réseau à Maven Central ;
> la compilation n'a donc pas pu être vérifiée automatiquement. Faites-le depuis
> votre poste :

```bash
cd rh
export TURSO_AUTH_TOKEN="votre_jeton_turso"
mvn clean package -DskipTests
java -jar target/rh.jar
# ou, en développement :
mvn spring-boot:run
```

L'application démarre sur http://localhost:8080.

## Compte par défaut

Un utilisateur administrateur est créé automatiquement au premier démarrage
(via `data.sql`) :

- **Utilisateur** : `admin`
- **Mot de passe** : `admin123`

⚠️ Changez ce mot de passe dès la première connexion (aucune page de gestion des
utilisateurs n'est fournie dans cette première version — vous pouvez insérer un
nouvel utilisateur directement en base avec un hash BCrypt, ou ajouter un écran
d'administration des comptes si besoin).

## Fonctionnement des affectations

Le formulaire « Nouvelle affectation » permet de choisir :
- un agent ;
- une date ;
- un ou plusieurs sites (sélection multiple) ;
- et/ou un lieu libre (texte) si l'affectation ne correspond à aucun site enregistré.

Une ligne d'affectation est créée pour chaque site coché, plus une ligne
supplémentaire si un lieu libre est renseigné — un même agent peut donc bien être
affecté le même jour à plusieurs endroits.

## Tableau de bord et export

- `/dashboard?debut=AAAA-MM-JJ&fin=AAAA-MM-JJ` affiche les statistiques
  (nombre d'affectations, répartition par statut, par lieu) et le détail des
  affectations sur la période choisie (par défaut : les 7 derniers jours).
- Le bouton **Exporter vers Excel** appelle `/export/excel?debut=...&fin=...`
  et télécharge un fichier `.xlsx` avec les mêmes données.

## Points à adapter avant une mise en production

- **Gestion des utilisateurs** : ajouter un écran (ou des commandes SQL) pour
  créer/désactiver des comptes plutôt que le seul compte `admin` fourni.
- **Rôles** : la colonne `role` de `utilisateurs` (ADMIN/USER) existe mais
  aucune restriction fine par rôle n'est appliquée dans `SecurityConfig` —
  à affiner selon vos besoins (ex. seul un ADMIN peut supprimer un agent).
- **HTTPS** : à activer en production (reverse proxy ou configuration Tomcat).
- **Jeton Turso** : à stocker dans un gestionnaire de secrets plutôt qu'en
  variable d'environnement brute si vous déployez sur une plateforme cloud.
