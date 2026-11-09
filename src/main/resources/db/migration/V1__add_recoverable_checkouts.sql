CREATE TABLE public."Checkout" (
    id uuid PRIMARY KEY,
    customer_id uuid NOT NULL REFERENCES public."User" (id) ON DELETE CASCADE,
    restaurant_id uuid NOT NULL REFERENCES public."Restaurant" (user_id) ON DELETE CASCADE,
    lines_json text NOT NULL,
    subtotal numeric(12, 2) NOT NULL CHECK (subtotal > 0),
    total_minor bigint NOT NULL CHECK (total_minor >= 50),
    payment_intent_id varchar(255) UNIQUE,
    completed boolean NOT NULL DEFAULT false,
    failure_reason varchar(255),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (NOT completed OR (payment_intent_id IS NOT NULL AND failure_reason IS NULL))
);
CREATE INDEX checkout_customer_id_idx ON public."Checkout" (customer_id);
CREATE INDEX checkout_restaurant_id_idx ON public."Checkout" (restaurant_id);
ALTER TABLE public."Checkout" ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public."Checkout" FROM anon, authenticated;
-- Checkout data is accessed through the authenticated backend, not directly through the public Data API.
