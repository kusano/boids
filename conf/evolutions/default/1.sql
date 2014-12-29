# --- !Ups
create table user (
    id bigint primary key auto_increment,

    name varchar(255) not null,
    password varchar(255) not null,
    reset_key varchar(255) not null,

    mailbox varchar(255) not null,

    pop_id varchar(255) not null,
    pop_password varchar(255) not null,

    created_at timestamp not null,
    modified_at timestamp not null,
    active int not null
);

create table address (
    address varchar(127) primary key,
    user bigint not null,
    memo varchar(1023) not null,

    created_at timestamp not null,
    modified_at timestamp not null,
    active int not null,

    foreign key (user) references user(id)
);

# -- !Downs
drop table user;
drop table address;
