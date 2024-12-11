# ENSIMAsso

Bienvenue sur le dépôt officiel du projet **ENSIMAsso**, une plateforme web centralisant les informations et services des associations étudiantes de l'ENSIM. Ce projet vise à offrir une interface moderne et intuitive pour les étudiants et les administrateurs des associations.

## Fonctionnalités principales

- **Page d'accueil** : 
  - Slider avec une brève présentation des associations.
  - Accès rapide aux pages des associations.
- **Forum** : 
  - Fil d'actualité interactif similaire aux réseaux sociaux.
- **Événements** : 
  - Affichage des événements à venir sous forme de timeline.
  - Boutons pour s'inscrire, partager ou consulter plus d'informations.
- **Associations et clubs** : 
  - Liste principale des associations avec descriptions.
  - Pages dédiées pour chaque association/club comprenant :
    - Description, membres, services, événements.
    - Formulaire d'adhésion.
- **Dashboard utilisateur** : 
  - Gestion des informations personnelles et des notifications.
- **Authentification** :
  - Connexion et inscription sécurisées.

## Technologies utilisées

### Frontend
- **React** : Pour une interface utilisateur réactive et moderne.
- **TailwindCSS** : Pour un design rapide et personnalisable.

### Backend
- **Spring Boot** : Pour une API robuste et performante.
- **MySQL** : Gestion des données relationnelles.

### Outils de développement
- **GitHub** : Hébergement du code source.
- **Docker** : Conteneurisation pour simplifier le déploiement.
- **Jenkins** : Automatisation des tests et des déploiements.

## Installation locale

### Prérequis
- Node.js et npm
- Java 17+
- Docker et Docker Compose

### Étapes
1. Clonez le dépôt :
   ```bash
   git clone https://github.com/Project5A/ensimasso.git
   ```
2. **Frontend** :
   ```bash
   cd frontend
   npm install
   npm start
   ```
3. **Backend** :
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
4. **Docker (optionnel)** :
   ```bash
   docker-compose up
   ```

## Contribution

Les contributions sont les bienvenues ! Veuillez suivre les étapes suivantes :
1. Forkez le dépôt.
2. Créez une branche pour vos modifications :
   ```bash
   git checkout -b feature/nom-de-la-fonctionnalite
   ```
3. Effectuez vos modifications et testez-les.
4. Soumettez une pull request en expliquant vos changements.

## Auteurs
- [Votre nom] ([Votre email])
- [Collaborateurs supplémentaires]

## Licence

Ce projet est sous licence MIT. Consultez le fichier `LICENSE` pour plus d'informations.
