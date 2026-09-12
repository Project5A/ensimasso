# Boucle de développement. L'objectif est qu'un inconnu clone le dépôt,
# lance `make up`, et ait un environnement fonctionnel.

SHELL := /bin/bash
COMPOSE := docker compose -f docker-compose.dev.yml
MVN := ./backend/mvnw -f backend/pom.xml

.DEFAULT_GOAL := aide

.PHONY: aide
aide: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: init
init: ## Crée .env à partir du modèle
	@test -f .env || (cp .env.example .env && echo "→ .env créé, pensez à le relire")

.PHONY: up
up: init ## Démarre l'infrastructure réellement utilisée par le code
	$(COMPOSE) up -d
	@echo "Postgres     localhost:5432  (via PgBouncer : 6432)"
	@echo "Keycloak     http://localhost:8081   admin / \$$KEYCLOAK_ADMIN_PASSWORD"
	@echo "Valkey       localhost:6379"
	@echo "MinIO        http://localhost:9001"
	@echo
	@echo "Redpanda, Meilisearch et Mailpit ne sont PAS démarrés : aucun code"
	@echo "ne les utilise encore. « make up-tout » si vous travaillez dessus."

.PHONY: up-tout
up-tout: init ## Démarre aussi ce qui n'est pas encore branché (Redpanda, Meilisearch, Mailpit)
	$(COMPOSE) --profile futur up -d
	@echo "Meilisearch  http://localhost:7700"
	@echo "Mailpit      http://localhost:8025"

.PHONY: down
down: ## Arrête l'infrastructure
	$(COMPOSE) --profile futur down

.PHONY: reset
reset: ## Repart d'une base vierge (DESTRUCTIF)
	$(COMPOSE) --profile futur down -v
	$(COMPOSE) up -d

.PHONY: run
run: ## Lance l'API en local (profil par défaut)
	$(MVN) spring-boot:run

.PHONY: run-delivery
run-delivery: ## Lance le profil de lecture publique, en lecture seule
	$(MVN) spring-boot:run -Dspring-boot.run.profiles=delivery

.PHONY: test
test: ## Tests unitaires + architecture (aucun Docker requis)
	$(MVN) test

.PHONY: verify
verify: ## Tout, y compris les tests d'intégration Testcontainers (Docker requis)
	$(MVN) verify

.PHONY: migrate
migrate: ## Applique les migrations Flyway
	$(MVN) flyway:migrate

.PHONY: seed
seed: ## Charge un jeu de données réaliste (3 assos, 2 années)
	@psql "$${DB_URL_PSQL:-postgresql://ensimasso:$${DB_PASSWORD:-dev_seulement}@localhost:5432/ensimasso}" \
		-v ON_ERROR_STOP=1 -f infra/postgres/seed-dev.sql
	@echo "→ jeu de données chargé"

.PHONY: front
front: ## Lance le portail public (proxy vers l'API locale)
	cd frontend && npm install --silent && VITE_API_PROXY=http://localhost:8080 npm run dev

.PHONY: front-test
front-test: ## Typage, tests et build du portail
	# npm ci d'abord : sans dépendances installées, « tsc » échoue sur
	# « Cannot find module 'react' », ce qui envoie chercher un problème de
	# typage là où il n'y a qu'un dossier node_modules absent.
	cd frontend && npm ci --no-audit --no-fund \
		&& npm run typecheck && npm test && npm run build

.PHONY: verif-contraintes
verif-contraintes: ## Prouve que les garanties sont bien portées par la base
	@echo "Chaque « doit etre REFUSE » doit produire une ERREUR : c'est le résultat attendu."
	@psql "$${DB_URL_PSQL:-postgresql://ensimasso:$${DB_PASSWORD:-dev_seulement}@localhost:5432/ensimasso}" \
		-f infra/postgres/verifier-contraintes.sql

.PHONY: lint
lint: ## Vérifie les frontières de modules et la compilation
	$(MVN) -q compile
	$(MVN) -q test -Dtest=ArchitectureTest

.PHONY: build
build: ## Construit le jar
	$(MVN) -DskipTests package

.PHONY: sauvegarde
sauvegarde: ## Sauvegarde chiffrée de la base et des objets (AGE_DESTINATAIRE requis)
	@infra/sauvegarde/sauvegarder.sh

.PHONY: drill
drill: ## Exercice de restauration complet, sur une base jetable
	@infra/sauvegarde/drill-restauration.sh $${PGDATABASE:-ensimasso}
