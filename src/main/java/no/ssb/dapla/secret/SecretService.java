package no.ssb.dapla.secret;

import io.grpc.stub.StreamObserver;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import no.ssb.dapla.secret.service.protobuf.GetKeyRequest;
import no.ssb.dapla.secret.service.protobuf.GetKeyResponse;
import no.ssb.dapla.secret.service.protobuf.SecretServiceGrpc;

public class SecretService extends SecretServiceGrpc.SecretServiceImplBase implements Service {

    final SecretRepository repository;

    public SecretService(SecretRepository repository) {
        this.repository = repository;
    }

    @Override
    public void update(Routing.Rules rules) {

    }

    @Override
    public void getKey(GetKeyRequest request, StreamObserver<GetKeyResponse> responseObserver) {
        super.getKey(request, responseObserver);
    }
}
