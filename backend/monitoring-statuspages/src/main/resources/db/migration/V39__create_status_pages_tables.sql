-- Public status pages and their components.
CREATE TABLE status_pages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    slug VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_status_pages_slug (slug),
    INDEX idx_status_pages_organization_id (organization_id)
);

CREATE TABLE status_page_components (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    status_page_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL,
    linked_health_check_id BIGINT NULL,
    INDEX idx_status_page_components_status_page_id (status_page_id),
    INDEX idx_status_page_components_health_check_id (linked_health_check_id),
    CONSTRAINT fk_status_page_components_page
        FOREIGN KEY (status_page_id) REFERENCES status_pages (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_status_page_components_health_check
        FOREIGN KEY (linked_health_check_id) REFERENCES health_checks (id)
        ON DELETE SET NULL
);
