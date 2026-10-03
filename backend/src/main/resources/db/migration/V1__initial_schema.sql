-- ============================================================================
-- V1 : CYPHER initial schema
-- ============================================================================
-- PostgreSQL 16. Generated from the project's authoritative schema dump and
-- adapted to run as a Flyway migration (see the generator's notes below).
--
-- Inventory: 16 tables, 23 foreign keys, 20 indexes, 10 triggers,
--            6 views, 8 functions, 1 stored procedure, 14 sequences.
--
-- Tables: users, roles, permissions, role_permissions, projects,
--         project_members, cameras, detections, incidents, alerts,
--         notifications, reports, media, audit_logs, ai_models, inference_jobs
--
-- All objects live in the `public` schema. We deliberately do NOT reset
-- search_path to '' the way pg_dump does: the trigger and procedure bodies below
-- use unqualified table names (e.g. `UPDATE alerts`), which are resolved when the
-- trigger fires, not when it is created. Emptying search_path would make every
-- trigger fail with "relation does not exist" at runtime.
--
-- Do not edit this migration once it has been applied -- add V2__*.sql instead.
-- ============================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14 (Ubuntu 16.14-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.14 (Ubuntu 16.14-0ubuntu0.24.04.1)

SET statement_timeout = 0;

SET lock_timeout = 0;

SET idle_in_transaction_session_timeout = 0;

SET client_encoding = 'UTF8';

SET standard_conforming_strings = on;

-- (search_path reset removed -- see migration header)
SET check_function_bodies = false;

SET xmloption = content;

SET client_min_messages = warning;

SET row_security = off;


--
-- Name: close_alerts_for_closed_incidents();
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.close_alerts_for_closed_incidents() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status = 'CLOSED' THEN
        UPDATE alerts
        SET alert_status = 'CLOSED'
        WHERE incident_id = NEW.incident_id;
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: create_incident_with_alert(bigint, bigint, character varying, text, character varying, bigint);
 Type: PROCEDURE;
 Schema: public;
 Owner: cypher_user
--

CREATE PROCEDURE public.create_incident_with_alert(IN p_project_id bigint, IN p_camera_id bigint, IN p_title character varying, IN p_description text, IN p_severity character varying, IN p_assigned_to bigint)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_incident_id BIGINT;
BEGIN
    -- Create incident
    INSERT INTO incidents (
        project_id,
        camera_id,
        title,
        description,
        severity,
        status,
        assigned_to,
        occurred_at
    )
    VALUES (
        p_project_id,
        p_camera_id,
        p_title,
        p_description,
        p_severity,
        'OPEN',
        p_assigned_to,
        CURRENT_TIMESTAMP
    )
    RETURNING incident_id INTO v_incident_id;

    -- Create matching alert
    INSERT INTO alerts (
        incident_id,
        alert_type,
        alert_message,
        alert_status,
        created_at
    )
    VALUES (
        v_incident_id,
        'INCIDENT',
        'New Incident: ' || p_title,
        'PENDING',
        CURRENT_TIMESTAMP
    );

END;
$$;


--
-- Name: dashboard_summary();
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.dashboard_summary() RETURNS TABLE(total_projects integer, total_cameras integer, total_incidents integer, total_detections integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        (SELECT COUNT(*)::INTEGER FROM projects),
        (SELECT COUNT(*)::INTEGER FROM cameras),
        (SELECT COUNT(*)::INTEGER FROM incidents),
        (SELECT COUNT(*)::INTEGER FROM detections);
END;
$$;


--
-- Name: get_camera_detection_count(bigint);
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.get_camera_detection_count(p_camera_id bigint) RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    total INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO total
    FROM detections
    WHERE camera_id = p_camera_id;

    RETURN total;
END;
$$;


--
-- Name: get_open_incidents();
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.get_open_incidents() RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    total INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO total
    FROM incidents
    WHERE status <> 'CLOSED';

    RETURN total;
END;
$$;


--
-- Name: get_project_camera_count(bigint);
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.get_project_camera_count(p_project_id bigint) RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    total INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO total
    FROM cameras
    WHERE project_id = p_project_id;

    RETURN total;
END;
$$;


--
-- Name: get_project_incident_count(bigint);
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.get_project_incident_count(p_project_id bigint) RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    total INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO total
    FROM incidents
    WHERE project_id = p_project_id;

    RETURN total;
END;
$$;


--
-- Name: log_incident_changes();
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.log_incident_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO audit_logs (
        user_id,
        event_type,
        entity_name,
        entity_id,
        old_values,
        new_values,
        created_at
    )
    VALUES (
        NEW.assigned_to,
        TG_OP,
        'incidents',
        NEW.incident_id,
        to_jsonb(OLD),
        to_jsonb(NEW),
        CURRENT_TIMESTAMP
    );

    RETURN NEW;
END;
$$;


--
-- Name: update_updated_at_column();
 Type: FUNCTION;
 Schema: public;
 Owner: cypher_user
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';


SET default_table_access_method = heap;


--
-- Name: alerts;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.alerts (
    alert_id bigint NOT NULL,
    incident_id bigint NOT NULL,
    alert_type character varying(50) NOT NULL,
    alert_message text NOT NULL,
    alert_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sent_at timestamp with time zone
);


--
-- Name: incidents;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.incidents (
    incident_id bigint NOT NULL,
    project_id bigint NOT NULL,
    camera_id bigint NOT NULL,
    detection_id bigint,
    title character varying(200) NOT NULL,
    description text,
    severity character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    assigned_to bigint,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: active_alerts;
 Type: VIEW;
 Schema: public;
 Owner: cypher_user
--

CREATE VIEW public.active_alerts AS
 SELECT a.alert_id,
    a.alert_type,
    a.alert_status,
    i.title,
    i.severity,
    i.status
   FROM (public.alerts a
     JOIN public.incidents i ON ((a.incident_id = i.incident_id)))
  WHERE ((a.alert_status)::text = 'PENDING'::text);


--
-- Name: ai_models;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.ai_models (
    model_id bigint NOT NULL,
    model_name character varying(100) NOT NULL,
    version character varying(50) NOT NULL,
    model_type character varying(100) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ai_models_model_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.ai_models_model_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_models_model_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.ai_models_model_id_seq OWNED BY public.ai_models.model_id;


--
-- Name: alerts_alert_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.alerts_alert_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: alerts_alert_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.alerts_alert_id_seq OWNED BY public.alerts.alert_id;


--
-- Name: audit_logs;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.audit_logs (
    audit_id bigint NOT NULL,
    user_id bigint,
    event_type character varying(20) NOT NULL,
    entity_name character varying(100) NOT NULL,
    entity_id bigint,
    old_values jsonb,
    new_values jsonb,
    ip_address character varying(45),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: audit_logs_audit_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.audit_logs_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_audit_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.audit_logs_audit_id_seq OWNED BY public.audit_logs.audit_id;


--
-- Name: camera_dashboard;
 Type: VIEW;
 Schema: public;
-- (superseded duplicate definition of view camera_dashboard removed; a later CREATE OR REPLACE wins)


--
-- Name: cameras;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.cameras (
    camera_id bigint NOT NULL,
    project_id bigint NOT NULL,
    camera_name character varying(100) NOT NULL,
    camera_code character varying(50) NOT NULL,
    location character varying(255),
    stream_url text,
    camera_status character varying(20) DEFAULT 'ONLINE'::character varying NOT NULL,
    latitude numeric(10,8),
    longitude numeric(11,8),
    installed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: cameras_camera_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.cameras_camera_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cameras_camera_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.cameras_camera_id_seq OWNED BY public.cameras.camera_id;


--
-- Name: detections;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.detections (
    detection_id bigint NOT NULL,
    camera_id bigint NOT NULL,
    job_id bigint NOT NULL,
    detected_object character varying(100) NOT NULL,
    confidence numeric(5,2) NOT NULL,
    bounding_box jsonb,
    image_path text,
    detected_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: daily_detection_statistics;
 Type: VIEW;
 Schema: public;
 Owner: cypher_user
--

CREATE VIEW public.daily_detection_statistics AS
 SELECT date(detected_at) AS detection_date,
    count(*) AS total_detections
   FROM public.detections
  GROUP BY (date(detected_at))
  ORDER BY (date(detected_at));


--
-- Name: detections_detection_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.detections_detection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: detections_detection_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.detections_detection_id_seq OWNED BY public.detections.detection_id;


--
-- Name: projects;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.projects (
    project_id bigint NOT NULL,
    project_name character varying(150) NOT NULL,
    description text,
    location character varying(255),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash text NOT NULL,
    phone character varying(20),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    role_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: incident_summary;
 Type: VIEW;
 Schema: public;
 Owner: cypher_user
--

CREATE VIEW public.incident_summary AS
 SELECT i.incident_id,
    i.title,
    i.severity,
    i.status,
    c.camera_name,
    p.project_name,
    (((u.first_name)::text || ' '::text) || (u.last_name)::text) AS assigned_user,
    i.occurred_at
   FROM (((public.incidents i
     JOIN public.cameras c ON ((i.camera_id = c.camera_id)))
     JOIN public.projects p ON ((i.project_id = p.project_id)))
     LEFT JOIN public.users u ON ((i.assigned_to = u.user_id)));


--
-- Name: incidents_incident_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.incidents_incident_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: incidents_incident_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.incidents_incident_id_seq OWNED BY public.incidents.incident_id;


--
-- Name: inference_jobs;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.inference_jobs (
    job_id bigint NOT NULL,
    camera_id bigint NOT NULL,
    model_id bigint NOT NULL,
    started_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp with time zone,
    status character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    frames_processed bigint DEFAULT 0
);


--
-- Name: inference_jobs_job_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.inference_jobs_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inference_jobs_job_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.inference_jobs_job_id_seq OWNED BY public.inference_jobs.job_id;


--
-- Name: media;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.media (
    media_id bigint NOT NULL,
    incident_id bigint,
    detection_id bigint,
    media_type character varying(20) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_path text NOT NULL,
    file_size bigint,
    uploaded_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: media_media_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.media_media_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: media_media_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.media_media_id_seq OWNED BY public.media.media_id;


--
-- Name: notifications;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.notifications (
    notification_id bigint NOT NULL,
    user_id bigint NOT NULL,
    alert_id bigint NOT NULL,
    notification_type character varying(50) NOT NULL,
    notification_status character varying(20) DEFAULT 'UNREAD'::character varying NOT NULL,
    delivered_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: notifications_notification_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.notifications_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_notification_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.notifications_notification_id_seq OWNED BY public.notifications.notification_id;


--
-- Name: permissions;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.permissions (
    permission_id bigint NOT NULL,
    permission_name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: permissions_permission_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.permissions_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_permission_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.permissions_permission_id_seq OWNED BY public.permissions.permission_id;


--
-- Name: project_members;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.project_members (
    project_id bigint NOT NULL,
    user_id bigint NOT NULL,
    joined_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: project_statistics;
 Type: VIEW;
 Schema: public;
 Owner: cypher_user
--

CREATE VIEW public.project_statistics AS
 SELECT p.project_id,
    p.project_name,
    count(DISTINCT c.camera_id) AS cameras,
    count(DISTINCT i.incident_id) AS incidents,
    count(DISTINCT d.detection_id) AS detections
   FROM (((public.projects p
     LEFT JOIN public.cameras c ON ((p.project_id = c.project_id)))
     LEFT JOIN public.incidents i ON ((p.project_id = i.project_id)))
     LEFT JOIN public.detections d ON ((c.camera_id = d.camera_id)))
  GROUP BY p.project_id, p.project_name;


--
-- Name: projects_project_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.projects_project_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: projects_project_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.projects_project_id_seq OWNED BY public.projects.project_id;


--
-- Name: reports;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.reports (
    report_id bigint NOT NULL,
    project_id bigint NOT NULL,
    report_name character varying(255) NOT NULL,
    report_type character varying(50) NOT NULL,
    generated_by bigint NOT NULL,
    generated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    report_file text
);


--
-- Name: reports_report_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.reports_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reports_report_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.reports_report_id_seq OWNED BY public.reports.report_id;


--
-- Name: role_permissions;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: roles;
 Type: TABLE;
 Schema: public;
 Owner: cypher_user
--

CREATE TABLE public.roles (
    role_id bigint NOT NULL,
    role_name character varying(50) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: roles_role_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.roles_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_role_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.roles_role_id_seq OWNED BY public.roles.role_id;


--
-- Name: users_user_id_seq;
 Type: SEQUENCE;
 Schema: public;
 Owner: cypher_user
--

CREATE SEQUENCE public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_user_id_seq;
 Type: SEQUENCE OWNED BY;
 Schema: public;
 Owner: cypher_user
--

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;


--
-- Name: ai_models model_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.ai_models ALTER COLUMN model_id SET DEFAULT nextval('public.ai_models_model_id_seq'::regclass);


--
-- Name: alerts alert_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.alerts ALTER COLUMN alert_id SET DEFAULT nextval('public.alerts_alert_id_seq'::regclass);


--
-- Name: audit_logs audit_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN audit_id SET DEFAULT nextval('public.audit_logs_audit_id_seq'::regclass);


--
-- Name: cameras camera_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.cameras ALTER COLUMN camera_id SET DEFAULT nextval('public.cameras_camera_id_seq'::regclass);


--
-- Name: detections detection_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.detections ALTER COLUMN detection_id SET DEFAULT nextval('public.detections_detection_id_seq'::regclass);


--
-- Name: incidents incident_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.incidents ALTER COLUMN incident_id SET DEFAULT nextval('public.incidents_incident_id_seq'::regclass);


--
-- Name: inference_jobs job_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.inference_jobs ALTER COLUMN job_id SET DEFAULT nextval('public.inference_jobs_job_id_seq'::regclass);


--
-- Name: media media_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.media ALTER COLUMN media_id SET DEFAULT nextval('public.media_media_id_seq'::regclass);


--
-- Name: notifications notification_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.notifications ALTER COLUMN notification_id SET DEFAULT nextval('public.notifications_notification_id_seq'::regclass);


--
-- Name: permissions permission_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.permissions ALTER COLUMN permission_id SET DEFAULT nextval('public.permissions_permission_id_seq'::regclass);


--
-- Name: projects project_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.projects ALTER COLUMN project_id SET DEFAULT nextval('public.projects_project_id_seq'::regclass);


--
-- Name: reports report_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.reports ALTER COLUMN report_id SET DEFAULT nextval('public.reports_report_id_seq'::regclass);


--
-- Name: roles role_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.roles ALTER COLUMN role_id SET DEFAULT nextval('public.roles_role_id_seq'::regclass);


--
-- Name: users user_id;
 Type: DEFAULT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);


--
-- Name: ai_models ai_models_model_name_version_key;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.ai_models
    ADD CONSTRAINT ai_models_model_name_version_key UNIQUE (model_name, version);


--
-- Name: ai_models ai_models_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.ai_models
    ADD CONSTRAINT ai_models_pkey PRIMARY KEY (model_id);


--
-- Name: alerts alerts_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.alerts
    ADD CONSTRAINT alerts_pkey PRIMARY KEY (alert_id);


--
-- Name: audit_logs audit_logs_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (audit_id);


--
-- Name: cameras cameras_camera_code_key;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.cameras
    ADD CONSTRAINT cameras_camera_code_key UNIQUE (camera_code);


--
-- Name: cameras cameras_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.cameras
    ADD CONSTRAINT cameras_pkey PRIMARY KEY (camera_id);


--
-- Name: detections detections_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.detections
    ADD CONSTRAINT detections_pkey PRIMARY KEY (detection_id);


--
-- Name: incidents incidents_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_pkey PRIMARY KEY (incident_id);


--
-- Name: inference_jobs inference_jobs_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.inference_jobs
    ADD CONSTRAINT inference_jobs_pkey PRIMARY KEY (job_id);


--
-- Name: media media_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.media
    ADD CONSTRAINT media_pkey PRIMARY KEY (media_id);


--
-- Name: notifications notifications_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (notification_id);


--
-- Name: permissions permissions_permission_name_key;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_permission_name_key UNIQUE (permission_name);


--
-- Name: permissions permissions_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: project_members project_members_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.project_members
    ADD CONSTRAINT project_members_pkey PRIMARY KEY (project_id, user_id);


--
-- Name: projects projects_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (project_id);


--
-- Name: reports reports_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (report_id);


--
-- Name: role_permissions role_permissions_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: roles roles_role_name_key;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_role_name_key UNIQUE (role_name);


--
-- Name: users users_email_key;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey;
 Type: CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: idx_alerts_incident;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_alerts_incident ON public.alerts USING btree (incident_id);


--
-- Name: idx_alerts_status;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_alerts_status ON public.alerts USING btree (alert_status);


--
-- Name: idx_cameras_project;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_cameras_project ON public.cameras USING btree (project_id);


--
-- Name: idx_cameras_status;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_cameras_status ON public.cameras USING btree (camera_status);


--
-- Name: idx_detections_camera;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_detections_camera ON public.detections USING btree (camera_id);


--
-- Name: idx_detections_job;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_detections_job ON public.detections USING btree (job_id);


--
-- Name: idx_detections_object;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_detections_object ON public.detections USING btree (detected_object);


--
-- Name: idx_detections_time;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_detections_time ON public.detections USING btree (detected_at);


--
-- Name: idx_incidents_camera;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_incidents_camera ON public.incidents USING btree (camera_id);


--
-- Name: idx_incidents_project;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_incidents_project ON public.incidents USING btree (project_id);


--
-- Name: idx_incidents_severity;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_incidents_severity ON public.incidents USING btree (severity);


--
-- Name: idx_incidents_status;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_incidents_status ON public.incidents USING btree (status);


--
-- Name: idx_jobs_camera;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_jobs_camera ON public.inference_jobs USING btree (camera_id);


--
-- Name: idx_jobs_model;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_jobs_model ON public.inference_jobs USING btree (model_id);


--
-- Name: idx_jobs_status;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_jobs_status ON public.inference_jobs USING btree (status);


--
-- Name: idx_notifications_status;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_notifications_status ON public.notifications USING btree (notification_status);


--
-- Name: idx_notifications_user;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_notifications_user ON public.notifications USING btree (user_id);


--
-- Name: idx_projects_status;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_projects_status ON public.projects USING btree (status);


--
-- Name: idx_users_email;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_role;
 Type: INDEX;
 Schema: public;
 Owner: cypher_user
--

CREATE INDEX idx_users_role ON public.users USING btree (role_id);


--
-- Name: camera_dashboard _RETURN;
 Type: RULE;
 Schema: public;
 Owner: cypher_user
--

CREATE OR REPLACE VIEW public.camera_dashboard AS
 SELECT c.camera_id,
    c.camera_name,
    c.camera_status,
    p.project_name,
    count(d.detection_id) AS total_detections
   FROM ((public.cameras c
     LEFT JOIN public.detections d ON ((c.camera_id = d.camera_id)))
     JOIN public.projects p ON ((c.project_id = p.project_id)))
  GROUP BY c.camera_id, p.project_name;


--
-- Name: cameras trg_cameras_updated;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_cameras_updated BEFORE UPDATE ON public.cameras FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: cameras trg_cameras_updated_at;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_cameras_updated_at BEFORE UPDATE ON public.cameras FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: incidents trg_close_alerts;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_close_alerts AFTER UPDATE ON public.incidents FOR EACH ROW EXECUTE FUNCTION public.close_alerts_for_closed_incidents();


--
-- Name: incidents trg_incident_changes;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_incident_changes AFTER UPDATE ON public.incidents FOR EACH ROW EXECUTE FUNCTION public.log_incident_changes();


--
-- Name: projects trg_projects_updated;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_projects_updated BEFORE UPDATE ON public.projects FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: projects trg_projects_updated_at;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_projects_updated_at BEFORE UPDATE ON public.projects FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: roles trg_roles_updated;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_roles_updated BEFORE UPDATE ON public.roles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: roles trg_roles_updated_at;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON public.roles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: users trg_users_updated;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: users trg_users_updated_at;
 Type: TRIGGER;
 Schema: public;
 Owner: cypher_user
--

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: alerts fk_alert_incident;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.alerts
    ADD CONSTRAINT fk_alert_incident FOREIGN KEY (incident_id) REFERENCES public.incidents(incident_id) ON DELETE CASCADE;


--
-- Name: audit_logs fk_audit_user;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: cameras fk_camera_project;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.cameras
    ADD CONSTRAINT fk_camera_project FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: detections fk_detection_camera;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.detections
    ADD CONSTRAINT fk_detection_camera FOREIGN KEY (camera_id) REFERENCES public.cameras(camera_id) ON DELETE CASCADE;


--
-- Name: detections fk_detection_job;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.detections
    ADD CONSTRAINT fk_detection_job FOREIGN KEY (job_id) REFERENCES public.inference_jobs(job_id) ON DELETE CASCADE;


--
-- Name: incidents fk_incident_camera;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT fk_incident_camera FOREIGN KEY (camera_id) REFERENCES public.cameras(camera_id);


--
-- Name: incidents fk_incident_detection;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT fk_incident_detection FOREIGN KEY (detection_id) REFERENCES public.detections(detection_id);


--
-- Name: incidents fk_incident_project;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT fk_incident_project FOREIGN KEY (project_id) REFERENCES public.projects(project_id);


--
-- Name: incidents fk_incident_user;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT fk_incident_user FOREIGN KEY (assigned_to) REFERENCES public.users(user_id);


--
-- Name: inference_jobs fk_job_camera;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.inference_jobs
    ADD CONSTRAINT fk_job_camera FOREIGN KEY (camera_id) REFERENCES public.cameras(camera_id) ON DELETE CASCADE;


--
-- Name: inference_jobs fk_job_model;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.inference_jobs
    ADD CONSTRAINT fk_job_model FOREIGN KEY (model_id) REFERENCES public.ai_models(model_id);


--
-- Name: media fk_media_detection;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.media
    ADD CONSTRAINT fk_media_detection FOREIGN KEY (detection_id) REFERENCES public.detections(detection_id) ON DELETE CASCADE;


--
-- Name: media fk_media_incident;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.media
    ADD CONSTRAINT fk_media_incident FOREIGN KEY (incident_id) REFERENCES public.incidents(incident_id) ON DELETE CASCADE;


--
-- Name: notifications fk_notification_alert;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id) REFERENCES public.alerts(alert_id) ON DELETE CASCADE;


--
-- Name: notifications fk_notification_user;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: role_permissions fk_permission;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_permission FOREIGN KEY (permission_id) REFERENCES public.permissions(permission_id) ON DELETE CASCADE;


--
-- Name: project_members fk_pm_project;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.project_members
    ADD CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: project_members fk_pm_user;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.project_members
    ADD CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: projects fk_project_creator;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: reports fk_report_project;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk_report_project FOREIGN KEY (project_id) REFERENCES public.projects(project_id);


--
-- Name: reports fk_report_user;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk_report_user FOREIGN KEY (generated_by) REFERENCES public.users(user_id);


--
-- Name: role_permissions fk_role;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: users fk_user_role;
 Type: FK CONSTRAINT;
 Schema: public;
 Owner: cypher_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES public.roles(role_id);


--
-- Name: SCHEMA public;
 Type: ACL;
 Schema: -;


--
-- PostgreSQL database dump complete
--

