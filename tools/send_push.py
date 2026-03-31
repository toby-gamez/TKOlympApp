#!/usr/bin/env python3
"""
FCM pusher script (HTTP v1).

Usage:
  - Interactive: run without --topics to pick cohorts from GraphQL and enter selection.
  - Non-interactive: provide --topics "2,3" or --all to send.

The script always adds `data.topic` (single) and `data.topics` (comma list when applicable)
so clients can display the message source reliably.

Requires: google-auth, requests (standard library urllib used here), OAuth2 service account JSON.
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error
import google.auth.transport.requests
import google.oauth2.service_account


# Default to a path stored in this repo user's environment — override with env var if needed
DEFAULT_SERVICE_ACCOUNT = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "/home/tobias/Downloads/tkolymp-3aced-c488435878fb.json")
# Default project id to use if FCM_PROJECT_ID not set
DEFAULT_PROJECT = os.environ.get("FCM_PROJECT_ID", "tkolymp-3aced")
GRAPHQL_URL = os.environ.get("GRAPHQL_URL", "https://api.rozpisovnik.cz/graphql")


def get_token(service_account_file: str) -> str:
    creds = google.oauth2.service_account.Credentials.from_service_account_file(
        service_account_file,
        scopes=["https://www.googleapis.com/auth/firebase.messaging"]
    )
    creds.refresh(google.auth.transport.requests.Request())
    return creds.token


def fetch_cohorts(tenant_id: str = "1"):
    query = {
        "query": """
        query MyQuery {
          cohortsList {
            id
            name
            isVisible
          }
        }
        """
    }
    req = urllib.request.Request(
        GRAPHQL_URL,
        data=json.dumps(query).encode(),
        headers={
            "Content-Type": "application/json",
            "x-tenant-id": tenant_id
        }
    )
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
        return data["data"]["cohortsList"]


def select_topics_interactive(cohorts):
    visible = [c for c in cohorts if c.get("isVisible")]
    if not visible:
        print("Žádné viditelné skupiny.")
        return None, None, []

    def print_list():
        print("\nDostupné skupiny:")
        for idx, c in enumerate(visible, start=1):
            print(f"{idx}. {c.get('id')} — {c.get('name') or '(no name)'}")

    def parse_input(s):
        s = s.strip()
        if not s:
            return []
        if s.lower() == 'all':
            return ['all']
        parts = [p.strip() for p in s.split(',') if p.strip()]
        out = []
        for p in parts:
            if '-' in p:
                try:
                    a, b = p.split('-', 1)
                    ia = int(a); ib = int(b)
                    for i in range(min(ia, ib), max(ia, ib) + 1):
                        if 1 <= i <= len(visible):
                            out.append(visible[i-1]['id'])
                except Exception:
                    # treat as literal id
                    out.append(p)
            else:
                # try numeric index
                try:
                    i = int(p)
                    if 1 <= i <= len(visible):
                        out.append(visible[i-1]['id'])
                    else:
                        out.append(p)
                except Exception:
                    out.append(p)
        return list(dict.fromkeys([x for x in out if x]))

    # interactive loop
    while True:
        print_list()
        print("\nZadej čísla/ID skupin oddělené čárkou (např. '1,3-5' nebo '2,4' nebo 'all'):")
        user_input = input("> ").strip()
        if not user_input:
            continue
        if user_input.lower() == 'q':
            return None, None, []
        sel_ids = parse_input(user_input)
        if not sel_ids:
            print("Neplatný výběr, zkus to znovu (nebo napiš 'all').")
            continue
        if sel_ids == ['all']:
            return 'all', None, ['all']
        condition = " || ".join([f"'{t}' in topics" for t in sel_ids])
        return None, condition, sel_ids


def send_push(service_account_file, project_id, title, body, topic, condition, topics, tenant_id="1"):
    token = get_token(service_account_file)
    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"

    message = {
        "notification": {"title": title, "body": body},
        "android": {"notification": {"channel_id": "coach"}}
    }

    # include data metadata so clients can detect the origin
    data = {}
    if topic == "all":
        message["topic"] = "all"
        data["topic"] = "all"
    elif topic is not None:
        message["topic"] = topic
        data["topic"] = topic
    elif condition is not None:
        message["condition"] = condition
        data["topics"] = ",".join(topics)
        # also include a primary topic so clients can show one label (pick first)
        if topics:
            data["topic"] = topics[0]

    if data:
        message["data"] = data

    payload = json.dumps({"message": message}).encode()

    req = urllib.request.Request(url, data=payload, headers={
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "x-tenant-id": tenant_id
    })

    try:
        with urllib.request.urlopen(req) as resp:
            resp_data = json.loads(resp.read())
            print("✅ Push odeslán!", json.dumps(resp_data, indent=2, ensure_ascii=False))
    except urllib.error.HTTPError as e:
        print("HTTPError:", e.code, e.read().decode(), file=sys.stderr)
        raise


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--service-account", "-s", default=DEFAULT_SERVICE_ACCOUNT, help="Path to service account JSON or set GOOGLE_APPLICATION_CREDENTIALS")
    p.add_argument("--project", "-p", default=DEFAULT_PROJECT, help="Firebase project id or set FCM_PROJECT_ID")
    p.add_argument("--tenant", default="1", help="x-tenant-id header for GraphQL and FCM (if used)")
    p.add_argument("--title", help="Notification title")
    p.add_argument("--body", help="Notification body")
    p.add_argument("--topics", help="Comma-separated topic ids (non-interactive)")
    p.add_argument("--all", action="store_true", help="Send to 'all' topic")
    args = p.parse_args()

    if not args.service_account:
        print("SERVICE_ACCOUNT_FILE is required (provide --service-account or set GOOGLE_APPLICATION_CREDENTIALS)")
        sys.exit(2)
    if not args.project:
        print("PROJECT_ID is required (provide --project or set FCM_PROJECT_ID)")
        sys.exit(2)

    title = args.title or input("Nadpis: ")
    body = args.body or input("Text: ")

    topic = None
    condition = None
    topics = []

    if args.all:
        topic = "all"
    elif args.topics:
        topics = [t.strip() for t in args.topics.split(",") if t.strip()]
        if len(topics) == 1:
            topic = topics[0]
        else:
            condition = " || ".join([f"'{t}' in topics" for t in topics])
    else:
        # interactive select
        cohorts = fetch_cohorts(args.tenant)
        topic, condition, topics = select_topics_interactive(cohorts)

    send_push(args.service_account, args.project, title, body, topic, condition, topics, tenant_id=args.tenant)


if __name__ == "__main__":
    main()
