-- V4: BTL code sequence for atomic, collision-free code generation

CREATE SEQUENCE IF NOT EXISTS btl_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;
