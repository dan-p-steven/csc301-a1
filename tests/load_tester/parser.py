from user import User, UserServiceRequest, Product
import shlex

import requests

import argparse


OBJ_MAPPING = {"USER": User, "PRODUCT": Product, "ORDER": None }
CMD_MAPPING = {"CREATE":"create", "DELETE": "delete", "UPDATE": "update"}

METHOD_MAPPING = {"CREATE": requests.post, "DELETE": requests.post, "UPDATE": requests.post, "GET": requests.get }
def stream_workload(workload_file: str):

    with open(workload_file, 'r') as file:
        for line in file:
            parts = shlex.split(line)
            yield parts

def generate_payload(line):
    match line[1]:
        case "CREATE" | "DELETE":

            # create payload for create and delete requests
            obj_type = line[0]
            obj_instance = OBJ_MAPPING[obj_type](*line[2:])

            payload = { "command": CMD_MAPPING[ line[1] ], **obj_instance.__dict__}

        case "UPDATE":

            # create the payload for an update request
            payload = {"command": CMD_MAPPING[ line[1] ],
                       "id": int(line[2])}
            for arg in line[3:]:
                if ":" in arg:
                    key, val = arg.split(":", 1)
                    payload[key] = val

        case "GET":

            # empty payload for GET
            payload = {"id": int(line[2]) }

        case _:

            payload = {}

    return payload

def send_single_request(args, line):

    payload = generate_payload(line)
    print(payload)

    base_url = f"{args.url}/{line[0].lower()}" 

    if line[1] == "GET":
        url = f"{base_url}/{line[2]}"
        response = requests.get(url)
    else:

        # post automatically sets Content-Type: application/json
        response = requests.post(
            base_url,
            json=payload
        )
    print (response.status_code)
    print (response.text)


def main():

    # create parser
    parser = argparse.ArgumentParser(description="Process a workload file.")

    # add --w argument
    parser.add_argument('-w', dest='workload_file', help='Path to the workload file', required=True)
    parser.add_argument('-u', dest='url', help='address:port of server', required=True)
    args = parser.parse_args()

    print(f"Opening file: {args.workload_file}")
    for line in stream_workload(args.workload_file):
        send_single_request(args, line)



if __name__ == "__main__":
    main()
