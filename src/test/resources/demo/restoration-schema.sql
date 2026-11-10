-- Isolated test schema matching the audited public columns, defaults and cascading foreign keys.
CREATE SCHEMA auth;
CREATE TABLE auth.users (id uuid PRIMARY KEY, email varchar NOT NULL UNIQUE);
CREATE TABLE public."User" (
    id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    first_name varchar NOT NULL DEFAULT '', last_name varchar NOT NULL DEFAULT '',
    username varchar NOT NULL DEFAULT '', role varchar NOT NULL DEFAULT ''
);
CREATE TABLE public."Customer" (
    user_id uuid PRIMARY KEY REFERENCES public."User"(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    address varchar NOT NULL DEFAULT '', latitude double precision NOT NULL, longitude double precision NOT NULL
);
CREATE TABLE public."Courier" (
    user_id uuid PRIMARY KEY REFERENCES public."User"(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state varchar NOT NULL DEFAULT '', latitude double precision NOT NULL, longitude double precision NOT NULL
);
CREATE TABLE public."Restaurant" (
    user_id uuid PRIMARY KEY REFERENCES public."User"(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    name varchar NOT NULL, status varchar NOT NULL, latitude double precision, longitude double precision
);
CREATE TABLE public."MenuItem" (
    id uuid PRIMARY KEY, restaurant_id uuid NOT NULL REFERENCES public."Restaurant"(user_id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    name varchar NOT NULL DEFAULT '', price numeric NOT NULL DEFAULT 0, stock_quantity integer NOT NULL DEFAULT 0,
    status varchar NOT NULL DEFAULT '', description varchar NOT NULL DEFAULT '', image_url varchar NOT NULL DEFAULT ''
);
CREATE TABLE public."Order" (
    id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
    restaurant_id uuid NOT NULL REFERENCES public."Restaurant"(user_id) ON DELETE CASCADE,
    courier_id uuid REFERENCES public."User"(id) ON DELETE CASCADE,
    state varchar NOT NULL, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal numeric, rejection_reason varchar, prepared_at timestamp with time zone,
    ready_at timestamp with time zone, delivered_at timestamp with time zone
);
CREATE TABLE public."OrderLineItem" (
    id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES public."Order"(id) ON DELETE CASCADE,
    menu_item_id uuid NOT NULL REFERENCES public."MenuItem"(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quantity integer NOT NULL, unit_price numeric NOT NULL, menu_item_name varchar
);
CREATE TABLE public."Event" (
    id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES public."Order"(id) ON DELETE CASCADE,
    performed_by uuid REFERENCES public."User"(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action varchar, previous_state varchar, new_state varchar NOT NULL
);
CREATE TABLE public."Bill" (
    id uuid PRIMARY KEY, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_id uuid REFERENCES public."Order"(id) ON DELETE CASCADE,
    user_id uuid REFERENCES public."User"(id) ON DELETE CASCADE,
    restaurant_id uuid REFERENCES public."Restaurant"(user_id) ON DELETE CASCADE,
    status varchar, subtotal numeric, total numeric, delivery_fee numeric, service_fee numeric,
    small_order_surchage numeric DEFAULT 0
);
