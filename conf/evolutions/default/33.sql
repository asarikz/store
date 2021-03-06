# --- 

# --- !Ups

create table transaction_paypal_status (
  transaction_paypal_status_id bigint not null,
  transaction_id bigint not null unique references transaction_header on delete cascade,
  status integer not null,
  token bigint not null,
  payment_type integer not null,
  constraint pk_transaction_paypal_status primary key (transaction_paypal_status_id)
);

create sequence transaction_paypal_status_seq start with 1000;

# --- !Downs

drop table transaction_paypal_status;

drop sequence transaction_paypal_status_seq;
