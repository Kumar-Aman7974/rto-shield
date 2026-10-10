# RTO Shield

Profit-aware order delivery and RTO recovery engine (CodeX 3.0, PS7). Backend only: demo through Swagger and Postman.

## Run it (first time, about 10 minutes)

1. Install: JDK 17, IntelliJ IDEA, Docker Desktop, Git, Postman.
2. Clone the repo and open it in IntelliJ (open the `pom.xml`, choose "Open as Project"). Wait for Maven to download.
3. In IntelliJ: Settings > Plugins, make sure **Lombok** is enabled. Settings > Build > Compiler > Annotation Processors > tick **Enable annotation processing**.
4. Start the database: `docker-compose up -d`
5. Run `RtoShieldApplication`. You should see `Started RtoShieldApplication` and `Seed data ready: 8 customers, 5 products, 3 couriers`.
6. Open http://localhost:8080/swagger-ui.html

## Smoke test (do this after EVERY merge)

`POST /demo/run` in Swagger. It must return an order with a score, a journey and a reason.
If it returns an error, the pipeline is broken: fix it before anything else.

## Seed data you can use in Postman

| What | Value |
|---|---|
| First-time customers | id 2 (Amit Verma, 9876500002), id 8 (Pooja Yadav, 9876500008) |
| Bad history | id 4 Rahul Das (-55), id 5 Vikram Singh (-120), id 7 Karan Mehta (-45) |
| Rs 199 order | product "Phone Case" (id 3) |
| Rs 2,999 order | product "Smart Watch" (id 4) |
| Bad pincodes | 110059, 226010 |
| Good pincodes | 400001, 560001 |

## Reset all data

`docker-compose down -v`, then `docker-compose up -d` and run the app again.

## Where things are

See `PROJECT_CONTEXT.md`: modules, who owns which folder, and the method contracts between you two.
