package projetocomunicacao.udp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import java.util.logging.Level;
import java.util.logging.Logger;
import projetocomunicacao.rede.Transporte;

public class ClienteUDP extends HostUDP implements Transporte {

    public ClienteUDP(String ipServidor, int porta) {
        this.ipServidor = ipServidor;
        this.porta = porta;
        this.portaEnviar = porta;
        enviando = new AtomicBoolean(false);
        recebendo = new AtomicBoolean(false);
    }

    private void reset() throws Exception {
        baos = new ByteArrayOutputStream();
        streamReceber = new BufferedOutputStream(baos);

        pacotesRecebidos = 0;
        windowBaseRecebe = 0;
        windowSizeRecebe = 5;

        buffer = new ConcurrentHashMap<Integer, PacketData>();
        mutexEnviar = new ReentrantLock();

        if (this.socketReceber == null || this.socketReceber.isClosed()) {
            while (true) {
                try {
                    this.socketReceber = new DatagramSocket(this.porta);
                    break;
                } catch (BindException e) {
                    this.socketReceber.close();
                }
            }
        }
    }

    private void reset(Object objeto) throws UnknownHostException {
        try {
            bufferDado = new ConcurrentHashMap<Integer, PacketData>();
            bufferAck = new ConcurrentHashMap<Integer, PacketACK>();

            ipEnviar = InetAddress.getByName(this.ipServidor);
            socket = new DatagramSocket();
            byte[] serializado = Serializer.serialize(objeto);
            stream = new BufferedInputStream(new ByteArrayInputStream(serializado));
            qntTotalPacotes = (int) Math.ceil(((double) serializado.length) / PacketData.tamanhoDados);

            mutex = new ReentrantLock();

            proxNumSeq = 0;
            windowBase = 0;
            windowSize = 10;

            timer = new Timer(true);

        } catch (UnknownHostException e) {
            throw new UnknownHostException();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void iniciarConexao() throws IOException, ConnectException, UnknownHostException {
        this.reset("ola");
        WriteData wd = new WriteData(this, true);
        WaitACK wa = new WaitACK(this, wd);
        wd.start();
        wa.start();
        try {
            wd.join();
            wa.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (wd.naoConectou.get()) {
            throw new ConnectException();
        }
    }

    @Override
    public void encerrarConexao() throws IOException {
        this.socketReceber.close();
    }

    @Override
    public void enviar(Object objeto) throws IOException {
        while (enviando.get());
        enviando.set(true);
        this.reset(objeto);
        WriteData wd = new WriteData(this, false);
        WaitACK wa = new WaitACK(this, wd);
        wd.start();
        wa.start();
    }

    @Override
    public Object receber() throws IOException, ClassNotFoundException {
        while (recebendo.get());
        recebendo.set(true);
        try {
            this.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
        ReadData r = new ReadData(this);
        r.start();
        while (recebendo.get());
        System.out.println("EU SOU CLIENTE E ESTOU RECEBENDO " + r.objeto.toString());
        return r.objeto;
    }

    public void setPortNumber(int portNumber) {
        this.porta = this.portaEnviar = portNumber;
        System.out.println("SETEI NOVA PORTA PARA " + portNumber);
    }

    public Socket getSocket() {
        return new Socket();
    }
}
