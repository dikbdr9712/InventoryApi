-- =====================================================================================
-- DK/Phar Inventory: database version 1 (every table the application needs).
--
-- Flyway runs this file by itself on an EMPTY database the first time the application starts.
-- Do not change this file after it has run anywhere: put later changes in a NEW file,
-- V2__what_changed.sql, V3__..., and Flyway applies them in order (see database/README.md).
--
-- Generated from the application's entity classes for MySQL 8 (Hibernate 7), 2 Oct 2026.
-- The data the application needs to work (roles and their permissions, the agreements,
-- the marketplace settings) is created by the application itself when it starts.
-- =====================================================================================

    create table audit_log (
        at datetime(6) not null,
        id bigint not null auto_increment,
        action varchar(50) not null,
        actor varchar(150) not null,
        target varchar(200),
        details varchar(1000),
        primary key (id)
    ) engine=InnoDB;

    create table contact_messages (
        id bigint not null auto_increment,
        submitted_at datetime(6),
        email varchar(255),
        message varchar(255),
        name varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table customers (
        created_at datetime(6),
        id bigint not null auto_increment,
        last_seen_at datetime(6),
        user_id bigint,
        first_source varchar(10),
        phone varchar(20),
        name varchar(120),
        email varchar(150),
        address varchar(500),
        notes varchar(1000),
        primary key (id)
    ) engine=InnoDB;

    create table delivery_areas (
        active bit not null,
        latitude float(53) not null,
        longitude float(53) not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        name varchar(80) not null,
        town varchar(80) not null,
        updated_by varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table earnings_ledger (
        amount decimal(12,2) not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        order_id bigint,
        package_id bigint,
        party_id bigint not null,
        party_type varchar(10) not null,
        entry_type varchar(20) not null,
        note varchar(300),
        created_by varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table inventory_stock (
        current_quantity integer not null,
        item_id bigint not null,
        last_updated datetime(6),
        stock_id bigint not null auto_increment,
        status varchar(255),
        primary key (stock_id)
    ) engine=InnoDB;

    create table item_master (
        cost_price decimal(10,2),
        discount_allowed bit,
        is_active bit,
        markup_percent decimal(5,2),
        max_discount_percent decimal(5,2),
        mrp decimal(10,2),
        selling_price decimal(10,2),
        tax_rate decimal(5,2),
        created_at datetime(6),
        item_id bigint not null auto_increment,
        seller_id bigint,
        delivery_size varchar(10),
        category varchar(100),
        barcode varchar(255),
        description varchar(255),
        image_path varchar(255),
        item_name varchar(255) not null,
        sku varchar(255) not null,
        supplier_item_code varchar(255),
        uom varchar(255),
        primary key (item_id)
    ) engine=InnoDB;

    create table legal_terms (
        version integer not null,
        id bigint not null auto_increment,
        published_at datetime(6) not null,
        terms_type varchar(20) not null,
        published_by varchar(150),
        title varchar(150) not null,
        change_summary varchar(500),
        body mediumtext not null,
        primary key (id)
    ) engine=InnoDB;

    create table marketplace_settings (
        bulky_base_fee decimal(10,2),
        bulky_per_km decimal(10,2),
        default_commission_percent decimal(5,2) not null,
        delivery_fee decimal(10,2) not null,
        included_km decimal(5,1),
        large_base_fee decimal(10,2),
        large_per_km decimal(10,2),
        max_distance_km decimal(6,1),
        medium_base_fee decimal(10,2),
        medium_per_km decimal(10,2),
        rider_pay_per_delivery decimal(10,2) not null,
        rider_share_percent decimal(5,2),
        shop_latitude float(53),
        shop_longitude float(53),
        small_base_fee decimal(10,2),
        small_per_km decimal(10,2),
        unknown_distance_km decimal(5,1),
        id bigint not null,
        updated_at datetime(6),
        shop_address varchar(300),
        updated_by varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table notifications (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        read_at datetime(6),
        type varchar(40) not null,
        user_email varchar(120) not null,
        title varchar(160) not null,
        link varchar(200),
        body varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table order_items (
        quantity integer not null,
        unit_price decimal(38,2) not null,
        item_id bigint not null,
        order_id bigint not null,
        order_item_id bigint not null auto_increment,
        package_id bigint,
        primary key (order_item_id)
    ) engine=InnoDB;

    create table order_packages (
        commission_amount decimal(12,2) not null,
        commission_percent decimal(5,2) not null,
        delivery_fee decimal(10,2) not null,
        distance_estimated bit,
        distance_km decimal(6,1),
        drop_latitude float(53),
        drop_longitude float(53),
        items_subtotal decimal(12,2) not null,
        pickup_latitude float(53),
        pickup_longitude float(53),
        rider_pay decimal(10,2) not null,
        seller_earning decimal(12,2) not null,
        delivery_code varchar(6),
        assigned_at datetime(6),
        cancelled_at datetime(6),
        created_at datetime(6),
        delivered_at datetime(6),
        id bigint not null auto_increment,
        order_id bigint not null,
        packed_at datetime(6),
        picked_up_at datetime(6),
        rider_id bigint,
        seller_id bigint,
        delivery_size varchar(10),
        status varchar(20) not null,
        drop_address varchar(500),
        pickup_address varchar(500),
        updated_by varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table orders (
        amount_tendered decimal(12,2),
        delivery_fee decimal(12,2),
        discount_amount decimal(12,2),
        drop_latitude float(53),
        drop_longitude float(53),
        tax_amount decimal(12,2),
        total_amount decimal(12,2) not null,
        created_at datetime(6) not null,
        customer_id bigint,
        order_id bigint not null auto_increment,
        shift_id bigint,
        updated_at datetime(6) not null,
        client_ref varchar(64),
        drop_location varchar(120),
        cashier varchar(150),
        address varchar(255),
        customer_email varchar(255),
        customer_name varchar(255),
        customer_phone varchar(255),
        note TEXT,
        order_status varchar(255),
        payment_method varchar(255),
        payment_status varchar(255),
        shipment_id varchar(255),
        source varchar(255) not null,
        updated_by varchar(255),
        primary key (order_id)
    ) engine=InnoDB;

    create table partner_documents (
        current bit not null,
        id bigint not null auto_increment,
        partner_id bigint not null,
        size_bytes bigint,
        uploaded_at datetime(6) not null,
        partner_type varchar(10) not null,
        kind varchar(30) not null,
        content_type varchar(60) not null,
        stored_name varchar(100) not null,
        original_name varchar(200),
        primary key (id)
    ) engine=InnoDB;

    create table password_reset_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        used_at datetime(6),
        user_id bigint not null,
        request_ip varchar(64),
        token_hash varchar(64) not null,
        primary key (id)
    ) engine=InnoDB;

    create table payment_intents (
        amount decimal(12,2) not null,
        currency varchar(3) not null,
        completed_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        order_id bigint not null,
        status varchar(12) not null,
        provider varchar(20) not null,
        reference varchar(40) not null,
        provider_reference varchar(80),
        customer_email varchar(120) not null,
        message varchar(300),
        primary key (id)
    ) engine=InnoDB;

    create table payments (
        amount decimal(38,2),
        order_id bigint,
        payment_date datetime(6),
        payment_id bigint not null auto_increment,
        transaction_id bigint,
        journal_number varchar(255),
        payment_method varchar(255),
        status varchar(255),
        primary key (payment_id)
    ) engine=InnoDB;

    create table pos_shifts (
        cash_refunds decimal(12,2),
        cash_sales decimal(12,2),
        counted_cash decimal(12,2),
        difference decimal(12,2),
        expected_cash decimal(12,2),
        opening_float decimal(12,2) not null,
        other_sales decimal(12,2),
        sale_count integer,
        closed_at datetime(6),
        id bigint not null auto_increment,
        opened_at datetime(6) not null,
        status varchar(10) not null,
        cashier_name varchar(120),
        cashier_email varchar(150) not null,
        closed_by varchar(150),
        closing_note varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table rider_profiles (
        license_expiry date,
        terms_version integer,
        created_at datetime(6),
        id bigint not null auto_increment,
        reviewed_at datetime(6),
        terms_accepted_at datetime(6),
        user_id bigint not null,
        cid_number varchar(20),
        emergency_contact_phone varchar(20),
        phone varchar(20),
        status varchar(20) not null,
        vehicle_number varchar(30),
        bank_account_number varchar(40),
        license_number varchar(40),
        vehicle_type varchar(40) not null,
        town varchar(80) not null,
        bank_account_name varchar(120),
        bank_name varchar(120),
        emergency_contact_name varchar(120),
        status_note varchar(500),
        reviewed_by varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table role_permissions (
        role_id bigint not null,
        permission varchar(60) not null,
        primary key (role_id, permission)
    ) engine=InnoDB;

    create table roles (
        catalog_version integer,
        permissions_initialized bit,
        id bigint not null auto_increment,
        description varchar(300),
        name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table sales_return_items (
        quantity integer not null,
        restocked bit not null,
        unit_refund decimal(12,2) not null,
        item_id bigint not null,
        order_item_id bigint not null,
        return_id bigint not null,
        return_item_id bigint not null auto_increment,
        primary key (return_item_id)
    ) engine=InnoDB;

    create table sales_returns (
        refund_amount decimal(12,2) not null,
        created_at datetime(6) not null,
        order_id bigint not null,
        return_id bigint not null auto_increment,
        refund_method varchar(30) not null,
        reason varchar(100) not null,
        created_by varchar(255),
        note varchar(255),
        primary key (return_id)
    ) engine=InnoDB;

    create table seller_profiles (
        commission_percent decimal(5,2),
        pickup_latitude float(53),
        pickup_longitude float(53),
        terms_version integer,
        created_at datetime(6),
        id bigint not null auto_increment,
        reviewed_at datetime(6),
        terms_accepted_at datetime(6),
        user_id bigint not null,
        cid_number varchar(20),
        phone varchar(20),
        status varchar(20) not null,
        tpn_number varchar(30),
        bank_account_number varchar(40),
        trade_license_number varchar(40),
        town varchar(80),
        bank_account_name varchar(120),
        bank_name varchar(120),
        shop_name varchar(120) not null,
        description varchar(500),
        pickup_address varchar(500) not null,
        status_note varchar(500),
        reviewed_by varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table shipments (
        created_at datetime(6),
        estimated_delivery datetime(6),
        id bigint not null auto_increment,
        order_id bigint,
        shipped_at datetime(6),
        courier varchar(255),
        created_by varchar(255),
        notes varchar(255),
        shipment_id varchar(255) not null,
        status varchar(255),
        tracking_number varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table tax_details (
        amount decimal(12,2) not null,
        rate decimal(5,2) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        order_id bigint not null,
        tax_type varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table terms_acceptances (
        version integer not null,
        accepted_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint,
        terms_type varchar(20) not null,
        ip_address varchar(64),
        user_email varchar(150) not null,
        user_agent varchar(300),
        primary key (id)
    ) engine=InnoDB;

    create table transactions (
        quantity integer,
        unit_price decimal(19,2),
        created_at datetime(6) not null,
        item_id bigint,
        reference_id bigint,
        transaction_id bigint not null auto_increment,
        reference_type varchar(20),
        customer_or_supplier varchar(100),
        notes varchar(255),
        transaction_type varchar(255),
        primary key (transaction_id)
    ) engine=InnoDB;

    create table users (
        active bit,
        created_at datetime(6),
        id bigint not null auto_increment,
        last_login_at datetime(6),
        password_changed_at datetime(6),
        role_id bigint not null,
        email varchar(255) not null,
        name varchar(255) not null,
        password varchar(255) not null,
        phone varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create index idx_audit_at 
       on audit_log (at);

    create index idx_customer_phone 
       on customers (phone);

    create index idx_customer_email 
       on customers (email);

    create index idx_customer_user 
       on customers (user_id);

    create index idx_ledger_party 
       on earnings_ledger (party_type, party_id);

    alter table inventory_stock 
       add constraint UKay2dai5w7p17li0cmk9hk8h8t unique (item_id);

    alter table item_master 
       add constraint UKtcvlxy4a4h05svytm26xswn6k unique (sku);

    alter table legal_terms 
       add constraint UKnif5qn6uqdrbxeqk9twrt9nop unique (terms_type, version);

    create index idx_notif_user 
       on notifications (user_email, created_at);

    create index idx_notif_unread 
       on notifications (user_email, read_at);

    create index idx_pkg_order 
       on order_packages (order_id);

    create index idx_pkg_seller 
       on order_packages (seller_id);

    create index idx_pkg_rider 
       on order_packages (rider_id);

    create index idx_pkg_status 
       on order_packages (status);

    alter table orders 
       add constraint UKfgcdfir4add9s2ruetx4yvra4 unique (client_ref);

    create index idx_doc_partner 
       on partner_documents (partner_type, partner_id);

    create index idx_reset_user 
       on password_reset_tokens (user_id);

    create index idx_reset_created 
       on password_reset_tokens (created_at);

    alter table password_reset_tokens 
       add constraint UKajre85ybxavf1tt4omkrs5p6g unique (token_hash);

    create index idx_intent_order 
       on payment_intents (order_id);

    create index idx_intent_status 
       on payment_intents (status, created_at);

    alter table payment_intents 
       add constraint UKo1ehkg4yqlntu1aws7whygs8l unique (reference);

    create index idx_shift_cashier 
       on pos_shifts (cashier_email, status);

    alter table rider_profiles 
       add constraint UKn2j4l8h8kicvr6xkgjoe974br unique (user_id);

    alter table roles 
       add constraint UKofx66keruapi6vyqpv6f2or37 unique (name);

    create index idx_sri_order_item 
       on sales_return_items (order_item_id);

    create index idx_sales_returns_order 
       on sales_returns (order_id);

    alter table seller_profiles 
       add constraint UK2264dwvu9q06u7388998fl3he unique (user_id);

    create index idx_terms_user 
       on terms_acceptances (user_email, terms_type);

    alter table users 
       add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table users 
       add constraint UKdu5v5sr43g5bfnji4vb8hg5s3 unique (phone);

    alter table payments 
       add constraint FKj0snkqpv28yu1my95jbeqvmgf 
       foreign key (transaction_id) 
       references transactions (transaction_id);

    alter table rider_profiles 
       add constraint FKd9p3fxlqny81bw9v9yg6yvbec 
       foreign key (user_id) 
       references users (id);

    alter table role_permissions 
       add constraint FKn5fotdgk8d1xvo8nav9uv3muc 
       foreign key (role_id) 
       references roles (id);

    alter table sales_return_items 
       add constraint FKr2u3qkpydsh695an9alpc5hsd 
       foreign key (return_id) 
       references sales_returns (return_id);

    alter table seller_profiles 
       add constraint FKcpr5ibp9058g7a9u58wh7xf2y 
       foreign key (user_id) 
       references users (id);

    alter table shipments 
       add constraint FKrnt4wht95lxxplspltrg9681s 
       foreign key (order_id) 
       references orders (order_id);

    alter table tax_details 
       add constraint FKi8rwmal36joxkyo7w0mgvjahn 
       foreign key (order_id) 
       references orders (order_id);

    alter table users 
       add constraint FKp56c1712k691lhsyewcssf40f 
       foreign key (role_id) 
       references roles (id);
