# DataFlow

DataFlow is a full-stack platform for uploading, validating, processing, and analyzing CSV and Excel files.

The goal of the project is to build a realistic software system that combines modern frontend development, backend architecture, data processing, cloud infrastructure, containerization, testing, and CI/CD.

## Main Features

- User authentication
- CSV and Excel file uploads
- File management
- Data validation
- Data quality analysis
- Basic statistical analysis
- Interactive data dashboards

## Architecture

The initial architecture will be based on:

- **Frontend:** React + TypeScript
- **Backend:** Java + Spring Boot
- **Data Processing:** Python + FastAPI
- **Database:** PostgreSQL
- **Containers:** Docker
- **CI/CD:** GitHub Actions
- **Cloud:** AWS
- **Infrastructure as Code:** Terraform

### High-level flow

```text
React + TypeScript
        |
        | REST API
        v
Spring Boot
        |
        +-------> PostgreSQL
        |
        | HTTP
        v
Python + FastAPI
        |
        v
Pandas / NumPy
```

## Project Structure

```text
dataflow/
├── frontend/
├── backend/
├── data-service/
├── docs/
├── .gitignore
├── docker-compose.yml
└── README.md
```

## Development Roadmap

- [ ] Project setup and repository configuration
- [ ] Backend architecture and PostgreSQL integration
- [ ] Authentication
- [ ] File management
- [ ] Frontend development
- [ ] Python data-processing service
- [ ] Data analysis and quality metrics
- [ ] Docker and automated testing
- [ ] CI/CD with GitHub Actions
- [ ] AWS deployment
- [ ] Infrastructure as Code with Terraform
- [ ] Monitoring and observability
- [ ] Performance and scalability improvements

## Status

🚧 **In development**
