from unittest.mock import Mock

import pytest
import requests

from ai.app.gateway import GatewayClient


def response(body):
    result = Mock()
    result.json.return_value = body
    result.text = str(body)
    result.ok = True
    return result


def test_login_is_cached_and_ingest_preserves_detection_contract(monkeypatch):
    post = Mock(side_effect=[response({'token': 'test-token'}), response({'incidentId': 7}), response({'incidentId': 8})])
    monkeypatch.setattr(requests, 'post', post)
    client = GatewayClient()
    for _ in range(2):
        client.report_incident('Knife', 'knife', 92, camera_id=3, bounding_box=[1, 2, 30, 40])
    assert post.call_count == 3
    call = post.call_args
    assert call.kwargs['headers'] == {'Authorization': 'Bearer test-token'}
    assert call.kwargs['json']['cameraId'] == 3
    assert call.kwargs['json']['boundingBox'] == [1, 2, 30, 40]
    assert call.kwargs['json']['confidence'] == 92
    assert call.kwargs['timeout'] == 10


def test_missing_token_is_rejected(monkeypatch):
    monkeypatch.setattr(requests, 'post', Mock(return_value=response({})))
    with pytest.raises(RuntimeError, match='no token'):
        GatewayClient()._login()


def test_expired_cached_login_refreshes(monkeypatch):
    post = Mock(return_value=response({'token': 'new-token'}))
    monkeypatch.setattr(requests, 'post', post)
    client = GatewayClient()
    client._token = 'old-token'
    client._token_at = 0
    assert client._auth_header()['Authorization'] == 'Bearer new-token'
    post.assert_called_once()


def test_gateway_outage_is_unhealthy(monkeypatch):
    monkeypatch.setattr(requests, 'get', Mock(side_effect=requests.ConnectionError()))
    assert GatewayClient().healthy() is False


def test_empty_camera_list_is_rejected(monkeypatch):
    client = GatewayClient()
    client._camera_id = None
    monkeypatch.setattr(client, 'cameras', lambda: [])
    with pytest.raises(RuntimeError, match='no cameras'):
        client.default_camera_id()
