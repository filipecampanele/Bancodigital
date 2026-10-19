import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Desafio DIO - Criando um Banco Digital com Java e Orientação a Objetos
 *
 * Pilares da POO demonstrados:
 *  - Abstração:        interface IConta e classe abstrata Conta
 *  - Encapsulamento:   atributos privados/protegidos + getters, sem setters de saldo
 *  - Herança:          ContaCorrente e ContaPoupanca estendem Conta
 *  - Polimorfismo:     imprimirExtrato() e sacar() sobrescritos nas subclasses
 *
 * Funcionalidades: saque, depósito, transferência e extrato.
 *
 * Como executar:
 *   javac BancoDigital.java
 *   java BancoDigital
 */
public class BancoDigital {

    public static void main(String[] args) {
        Banco banco = new Banco("Banco Digital DIO");

        Cliente venilton = new Cliente("Venilton", "123.456.789-00");
        Cliente maria = new Cliente("Maria", "987.654.321-00");

        Conta ccVenilton = banco.abrirContaCorrente(venilton);
        Conta poupVenilton = banco.abrirContaPoupanca(venilton);
        Conta ccMaria = banco.abrirContaCorrente(maria);

        System.out.println("=== Contas do " + banco.getNome() + " ===");
        banco.listarContas();

        System.out.println("\n=== Operações ===");
        ccVenilton.depositar(1000);
        ccVenilton.transferir(300, poupVenilton);
        ccVenilton.transferir(200, ccMaria);
        ccVenilton.sacar(150);
        poupVenilton.sacar(50);

        // Tentativas inválidas (tratadas sem derrubar o programa)
        tentar(() -> ccVenilton.sacar(99999));
        tentar(() -> ccVenilton.depositar(-10));
        tentar(() -> ccMaria.transferir(50, null));

        // Conta corrente permite usar cheque especial
        tentar(() -> ccMaria.sacar(400)); // saldo 200 + limite 500

        System.out.println("\n=== Extratos ===");
        ccVenilton.imprimirExtrato();
        poupVenilton.imprimirExtrato();
        ccMaria.imprimirExtrato();
    }

    private static void tentar(Runnable operacao) {
        try {
            operacao.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("[ERRO] " + e.getMessage());
        }
    }
}

/* ======================= ABSTRAÇÃO ======================= */

interface IConta {
    void sacar(double valor);

    void depositar(double valor);

    void transferir(double valor, IConta contaDestino);

    void imprimirExtrato();
}

/* ======================= CLIENTE ======================= */

class Cliente {
    private final String nome;
    private final String cpf;

    public Cliente(String nome, String cpf) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório.");
        }
        this.nome = nome;
        this.cpf = cpf;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }
}

/* ======================= TRANSAÇÃO (para o extrato) ======================= */

class Transacao {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final LocalDateTime data = LocalDateTime.now();
    private final String descricao;
    private final double valor; // positivo = entrada, negativo = saída
    private final double saldoApos;

    public Transacao(String descricao, double valor, double saldoApos) {
        this.descricao = descricao;
        this.valor = valor;
        this.saldoApos = saldoApos;
    }

    @Override
    public String toString() {
        return String.format("%s | %-28s | %+10.2f | Saldo: %10.2f",
                data.format(FORMATO), descricao, valor, saldoApos);
    }
}

/* ======================= HERANÇA + ENCAPSULAMENTO ======================= */

abstract class Conta implements IConta {

    private static final int AGENCIA_PADRAO = 1;
    private static int sequencial = 1;

    protected final int agencia;
    protected final int numero;
    protected double saldo; // protegido: só alterado por métodos da própria hierarquia
    protected final Cliente cliente;
    private final List<Transacao> transacoes = new ArrayList<>();

    public Conta(Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente é obrigatório.");
        }
        this.agencia = AGENCIA_PADRAO;
        this.numero = sequencial++;
        this.cliente = cliente;
    }

    /** Cada tipo de conta define quanto pode ser sacado (polimorfismo). */
    protected abstract double limiteDisponivel();

    /** Cada tipo de conta define seu nome no extrato. */
    protected abstract String getTipo();

    @Override
    public void sacar(double valor) {
        validarValor(valor);
        if (valor > limiteDisponivel()) {
            throw new IllegalStateException(String.format(
                    "Saldo insuficiente na conta %d. Disponível: R$ %.2f, solicitado: R$ %.2f",
                    numero, limiteDisponivel(), valor));
        }
        saldo -= valor;
        registrar("Saque", -valor);
        System.out.printf("Saque de R$ %.2f realizado na conta %d.%n", valor, numero);
    }

    @Override
    public void depositar(double valor) {
        validarValor(valor);
        saldo += valor;
        registrar("Depósito", valor);
        System.out.printf("Depósito de R$ %.2f realizado na conta %d.%n", valor, numero);
    }

    @Override
    public void transferir(double valor, IConta contaDestino) {
        if (contaDestino == null) {
            throw new IllegalArgumentException("Conta de destino inválida.");
        }
        if (contaDestino == this) {
            throw new IllegalArgumentException("Não é possível transferir para a mesma conta.");
        }
        validarValor(valor);
        if (valor > limiteDisponivel()) {
            throw new IllegalStateException(String.format(
                    "Saldo insuficiente para transferir R$ %.2f. Disponível: R$ %.2f",
                    valor, limiteDisponivel()));
        }
        saldo -= valor;
        registrar("Transferência enviada", -valor);

        // Destino recebe como depósito interno (sem imprimir mensagem de depósito)
        if (contaDestino instanceof Conta destino) {
            destino.saldo += valor;
            destino.registrar("Transferência recebida", valor);
            System.out.printf("Transferência de R$ %.2f da conta %d para a conta %d realizada.%n",
                    valor, numero, destino.numero);
        } else {
            contaDestino.depositar(valor);
        }
    }

    @Override
    public void imprimirExtrato() {
        System.out.println("\n===== EXTRATO - " + getTipo() + " =====");
        imprimirInfosComuns();
        System.out.println("-----------------------------------------------------------------------");
        if (transacoes.isEmpty()) {
            System.out.println("Nenhuma movimentação.");
        } else {
            transacoes.forEach(System.out::println);
        }
        System.out.println("-----------------------------------------------------------------------");
        System.out.printf("Saldo atual: R$ %.2f%n", saldo);
    }

    protected void imprimirInfosComuns() {
        System.out.printf("Titular: %s%n", cliente.getNome());
        System.out.printf("Agência: %d | Conta: %d%n", agencia, numero);
    }

    protected void registrar(String descricao, double valor) {
        transacoes.add(new Transacao(descricao, valor, saldo));
    }

    private void validarValor(double valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser maior que zero.");
        }
    }

    /* Getters (sem setter de saldo: encapsulamento) */
    public int getAgencia() {
        return agencia;
    }

    public int getNumero() {
        return numero;
    }

    public double getSaldo() {
        return saldo;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public List<Transacao> getTransacoes() {
        return Collections.unmodifiableList(transacoes);
    }

    @Override
    public String toString() {
        return String.format("%s | Ag: %d | Conta: %d | Titular: %s | Saldo: R$ %.2f",
                getTipo(), agencia, numero, cliente.getNome(), saldo);
    }
}

class ContaCorrente extends Conta {

    private static final double LIMITE_CHEQUE_ESPECIAL = 500.0;

    public ContaCorrente(Cliente cliente) {
        super(cliente);
    }

    @Override
    protected double limiteDisponivel() {
        return saldo + LIMITE_CHEQUE_ESPECIAL; // polimorfismo: permite cheque especial
    }

    @Override
    protected String getTipo() {
        return "Conta Corrente";
    }

    @Override
    protected void imprimirInfosComuns() {
        super.imprimirInfosComuns();
        System.out.printf("Cheque especial: R$ %.2f%n", LIMITE_CHEQUE_ESPECIAL);
    }
}

class ContaPoupanca extends Conta {

    private static final double RENDIMENTO_MENSAL = 0.005; // 0,5%

    public ContaPoupanca(Cliente cliente) {
        super(cliente);
    }

    @Override
    protected double limiteDisponivel() {
        return saldo; // polimorfismo: poupança não tem cheque especial
    }

    @Override
    protected String getTipo() {
        return "Conta Poupança";
    }

    /** Funcionalidade exclusiva da poupança. */
    public void aplicarRendimentoMensal() {
        double rendimento = saldo * RENDIMENTO_MENSAL;
        if (rendimento > 0) {
            saldo += rendimento;
            registrar("Rendimento mensal", rendimento);
        }
    }
}

/* ======================= BANCO ======================= */

class Banco {
    private final String nome;
    private final List<Conta> contas = new ArrayList<>();

    public Banco(String nome) {
        this.nome = nome;
    }

    public Conta abrirContaCorrente(Cliente cliente) {
        Conta conta = new ContaCorrente(cliente);
        contas.add(conta);
        return conta;
    }

    public Conta abrirContaPoupanca(Cliente cliente) {
        Conta conta = new ContaPoupanca(cliente);
        contas.add(conta);
        return conta;
    }

    public void listarContas() {
        contas.forEach(System.out::println);
    }

    public String getNome() {
        return nome;
    }

    public List<Conta> getContas() {
        return Collections.unmodifiableList(contas);
    }
}
