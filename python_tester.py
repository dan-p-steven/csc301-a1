import requests
import json

import json
import requests

def run_tests(testcases_file, responses_file, base_url):
    # Load test cases and expected responses
    with open(testcases_file, 'r') as f:
        test_cases = json.load(f)
    
    with open(responses_file, 'r') as f:
        expected_responses = json.load(f)
    
    # Stats
    total = 0
    matches = 0
    mismatches = 0
    
    for test_name in test_cases:
        total += 1
        request_payload = test_cases[test_name]
        expected_response = expected_responses[test_name]
        
        # Extract expected status code from test name
        expected_status = extract_status_code(test_name)
        
        print(f"\n{'='*70}")
        print(f"Test: {test_name}")
        print(f"Request: {json.dumps(request_payload, indent=2)}")
        
        try:
            # Send HTTP POST request (adjust as needed)
            response = requests.post(
                #f"{base_url}/user",
                f"{base_url}/product",
                json=request_payload,
                timeout=10
            )
            
            actual_status = response.status_code
            actual_response = response.json() if response.text else {}
            
            # Display status code
            print(f"Status Code: {actual_status}")
            print(f"Actual Response: {json.dumps(actual_response, indent=2)}")
            print(f"Expected Response: {json.dumps(expected_response, indent=2)}")
            
            # Compare status code and response body
            status_match = actual_status == expected_status
            body_match = actual_response == expected_response
            
            if status_match and body_match:
                print("Result: MATCH ✓")
                matches += 1
            else:
                print("Result: NOT MATCH ✗")
                if not status_match:
                    print(f"  - Status mismatch: expected {expected_status}, got {actual_status}")
                if not body_match:
                    print(f"  - Body mismatch")
                mismatches += 1
                
        except requests.exceptions.RequestException as e:
            print(f"Error: {e}")
            print("Result: NOT MATCH ✗ (request failed)")
            mismatches += 1
        except json.JSONDecodeError:
            print("Error: Response is not valid JSON")
            print("Result: NOT MATCH ✗ (invalid JSON)")
            mismatches += 1
    
    # Summary
    print(f"\n{'='*70}")
    print(f"SUMMARY")
    print(f"Total Tests: {total}")
    print(f"Matches: {matches}")
    print(f"Mismatches: {mismatches}")
    print(f"Success Rate: {(matches/total)*100:.1f}%")

def extract_status_code(test_name):
    """Extract expected status code from test name like 'user_create_200_1000'"""
    parts = test_name.split('_')
    for part in parts:
        # Handle comma-separated codes, take the first one
        if ',' in part:
            codes = [code for code in part.split(',') if code.isdigit()]
            if codes:
                return int(codes[0])
        # Handle single code
        if part.isdigit() and len(part) == 3:
            return int(part)
    return None

if __name__ == "__main__":
    run_tests(
        testcases_file="project_outline/CSC301_A1_testcases/payloads/product_testcases.json",
        responses_file="project_outline/CSC301_A1_testcases/responses/product_responses.json",
        base_url="http://localhost:14002"  # Change to your server URL
        #testcases_file="project_outline/CSC301_A1_testcases/payloads/user_testcases.json",
        #responses_file="project_outline/CSC301_A1_testcases/responses/user_responses.json",
        #base_url="http://localhost:14833"  # Change to your server URL
        #testcases_file="project_outline/CSC301_A1_testcases/payloads/product_testcases.json",
        #responses_file="project_outline/CSC301_A1_testcases/responses/product_responses.json",
        #base_url="http://localhost:14002"  # Change to your server URL
    )
