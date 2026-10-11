package com.rtoshield.testsupport;

import com.rtoshield.domain.AuditLog;
import com.rtoshield.domain.ConfigEntry;
import com.rtoshield.domain.Courier;
import com.rtoshield.domain.CourierPincodeStat;
import com.rtoshield.repo.AuditLogRepository;
import com.rtoshield.repo.ConfigEntryRepository;
import com.rtoshield.repo.CourierPincodeStatRepository;
import com.rtoshield.repo.CourierRepository;

import org.springframework.data.domain.Pageable;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tiny in-memory stand-ins for repositories, built with plain Java proxies.
 * No Mockito and no database, so tests run in milliseconds on any Java version.
 */
public final class Fakes {

    private Fakes() {
    }

    public interface Handler {
        Object handle(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> handler.handle(method.getName(), args == null ? new Object[0] : args));
    }

    /** config_entries table backed by a map. An empty map means "use the defaults from ConfigKeys". */
    public static ConfigEntryRepository configRepo(Map<String, ConfigEntry> rows) {
        return proxy(ConfigEntryRepository.class, (method, args) -> switch (method) {
            case "findById" -> Optional.ofNullable(rows.get((String) args[0]));
            case "existsById" -> rows.containsKey((String) args[0]);
            case "findAll" -> new ArrayList<>(rows.values());
            case "save" -> {
                ConfigEntry entry = (ConfigEntry) args[0];
                rows.put(entry.getKey(), entry);
                yield entry;
            }
            default -> throw new UnsupportedOperationException("ConfigEntryRepository." + method);
        });
    }

    /** audit_log table backed by a list (ids are 1, 2, 3 ... in the order rows are saved). */
    public static AuditLogRepository auditRepo(List<AuditLog> rows) {
        return proxy(AuditLogRepository.class, (method, args) -> switch (method) {
            case "save" -> {
                AuditLog row = (AuditLog) args[0];
                if (row.getId() == null) {
                    row.setId(rows.size() + 1L);
                }
                rows.add(row);
                yield row;
            }
            case "findByOrderIdOrderByCreatedAtAsc" -> rows.stream()
                    .filter(r -> Objects.equals(r.getOrderId(), args[0])).toList();
            case "findAllByOrderByIdDesc" -> {
                int size = ((Pageable) args[0]).getPageSize();
                List<AuditLog> newestFirst = new ArrayList<>(rows);
                Collections.reverse(newestFirst);
                yield newestFirst.stream().limit(size).toList();
            }
            default -> throw new UnsupportedOperationException("AuditLogRepository." + method);
        });
    }

    public static void setConfig(Map<String, ConfigEntry> rows, String key, String value) {
        rows.put(key, new ConfigEntry(key, value, "test"));
    }

    /** courier_pincode_stats table. */
    public static final class StatStore {
        public final List<CourierPincodeStat> rows = new ArrayList<>();

        /** Replaces the rows of one pincode: one row per courier (ids 1, 2, 3...), 100 attempts each. */
        public void setPincode(String pincode, int... successesPerCourier) {
            rows.removeIf(r -> r.getPincode().equals(pincode));
            for (int i = 0; i < successesPerCourier.length; i++) {
                Courier courier = new Courier();
                courier.setId(i + 1L);
                courier.setName("Courier" + (i + 1));
                CourierPincodeStat stat = new CourierPincodeStat();
                stat.setCourier(courier);
                stat.setPincode(pincode);
                stat.setAttempts(100);
                stat.setSuccesses(successesPerCourier[i]);
                rows.add(stat);
            }
        }

        public CourierPincodeStatRepository repo() {
            return proxy(CourierPincodeStatRepository.class, (method, args) -> switch (method) {
                case "findByPincode" -> rows.stream().filter(r -> r.getPincode().equals(args[0])).toList();
                case "findByCourierIdAndPincode" -> rows.stream()
                        .filter(r -> r.getCourier().getId().equals(args[0]) && r.getPincode().equals(args[1]))
                        .findFirst();
                case "save" -> {
                    if (!rows.contains(args[0])) {
                        rows.add((CourierPincodeStat) args[0]);
                    }
                    yield args[0];
                }
                default -> throw new UnsupportedOperationException("CourierPincodeStatRepository." + method);
            });
        }
    }

    /** couriers table holding exactly these couriers. */
    public static CourierRepository courierRepo(List<Courier> couriers) {
        return proxy(CourierRepository.class, (method, args) -> switch (method) {
            case "findAll" -> List.copyOf(couriers);
            case "findById" -> couriers.stream().filter(c -> c.getId().equals(args[0])).findFirst();
            default -> throw new UnsupportedOperationException("CourierRepository." + method);
        });
    }

    /** couriers table: every id exists. */
    public static CourierRepository courierRepo() {
        return proxy(CourierRepository.class, (method, args) -> switch (method) {
            case "findById" -> {
                Courier courier = new Courier();
                courier.setId((Long) args[0]);
                courier.setName("Courier" + args[0]);
                yield Optional.of(courier);
            }
            default -> throw new UnsupportedOperationException("CourierRepository." + method);
        });
    }
}
