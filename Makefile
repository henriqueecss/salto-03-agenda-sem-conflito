up:
	docker compose up -d --wait

down:
	docker compose down

run: up
	./mvnw spring-boot:run

test:
	./mvnw verify

.PHONY: up down run test
