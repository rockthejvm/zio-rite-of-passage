# ZIO Rite of Passage - Scala review board

This is the code that we write in the [ZIO Rite of Passage](https://rockthejvm.com/courses/zio-rite-of-passage) course on Rock the JVM.

This repository contains the application we write in the course: a review-board for companies, in the style of Glassdoor. The application features
- user management
- an email system
- user-generated content
- AI integration
- full credit card checkout with Stripe checkout sessions
- various forms of data storage and retrieval (CRUD-style)

The application is built with 
- [ZIO](https://zio.dev) for effects, tests, config, logging and others
- [Tapir](https://tapir.softwaremill.com/) for HTTP definitions, with ZIO HTTP as the server
- Quill for data storage
- Flyway for migrations
- Java mail for emails
- Stripe for checkout
- [Laminar](https://laminar.dev) on the frontend, integrated with ZIO

## How to run

-   have a docker database ready - this means, for dev purposes, `docker-compose up` in the root folder
-   in another terminal `sbt`, then `project server`, then `~compile` to compile the server incrementally as you develop
-   in the same terminal `runMain com.rockthejvm.reviewboard.Application` to start the server 
-   in another terminal `sbt`, `project app` and `~fastOptJS` to compile the frontend
-   in another terminal (that's 4 in total), go to the `app/` directory, run `npm install`
-   still in terminal 4, run `npm run start` to start serving the page
-   go to `http://localhost:1234` to see the page

## For questions or suggestions

If you have changes to suggest to this repo, either
- submit a GitHub issue
- tell me in the course Q/A forum
- submit a pull request!