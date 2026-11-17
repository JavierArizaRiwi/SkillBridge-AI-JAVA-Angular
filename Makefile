.PHONY: up down reset logs ps observability test-back test-front build

up:
	docker compose up --build -d

down:
	docker compose down

reset:
	docker compose down -v --remove-orphans
	docker compose up --build -d

logs:
	docker compose logs -f --tail=200

ps:
	docker compose ps

observability:
	docker compose --profile observability up --build -d

test-back:
	docker compose run --rm backend mvn test

test-front:
	cd frontend && npm ci && npm test -- --watch=false

build:
	docker compose build
